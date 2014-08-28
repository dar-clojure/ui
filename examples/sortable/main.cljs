(ns sortable.main
  (:require [dar.ui.frp :as frp :include-macros true]
            [dar.ui :as ui]
            [util :as util])
  (:require-macros [dar.ui.html :refer [DIV IMG]]))

(enable-console-print!)

(defn mouse-event-pos [e]
  [(.-screenX e) (.-screenY e)])

(def mouse-position
  (util/event-port js/window :mousemove
    (fn [] [0 0])
    mouse-event-pos))

(def mouseup
  (util/event-port* js/window :mouseup
    (constantly true)))

(defn initial-state [col]
  (let [capture (frp/new-event)
        html (frp/<- (fn [col]
                       (ui/set-children col
                         (map (fn [el]
                                (ui/listen el :mousedown
                                  (ui/to capture (fn [e]
                                                   [(ui/key el) (mouse-event-pos e)]))))
                           (ui/children col))))
               col)]
    {:commands {:capture capture}
     :inputs {:col html}
     :html html
     :methods {:capture captured-state}}))

(defn captured-state [state [k pos]]
  (-> state
    (update-in [:commands] assoc
      :mouse mouse-position
      :mouseup mouseup)
    (assoc
      :start pos
      :initial-state state
      :methods captured-state-methods
      :html (frp/new-signal
              (-> state :values :col)))))

(def captured-state-methods
  {:mouse (fn [state pos]
            (println pos)
            state)
   :mouseup (fn [state _]
              (:initial-state state))})

(defn sortable [col]
  (frp/switch :html
    (util/state-machine
      (initial-state col))))

(def app
  (frp/new-app))

(defn -main []
  (ui/render!
    app
    (sortable
      (frp/new-signal
        (DIV nil
          [(for [i (range 1 11)]
             (IMG {:key i :src "http://lorempixel.com/120/120/nature/"}))])))
    (js/document.getElementById "app")))
