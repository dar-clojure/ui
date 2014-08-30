(ns sortable.main
  (:require [dar.ui.frp :as frp :include-macros true]
            [dar.ui :as ui]
            [util :as util])
  (:require-macros [dar.ui.html :refer [DIV IMG]]))

(enable-console-print!)

(def counter (atom 0))

(defn new-uid []
  (swap! counter inc))

(defn mouse-pos [e]
  [(+ js/window.scrollX (.-screenX e))
   (+ js/window.scrollY (.-screenY e))])

(defn element-pos [el]
  (let [r (.getBoundingClientRect el) ]
    [(+ js/window.scrollX (.-left r))
     (+ js/window.scrollY (.-top r))]))

(defn mouse-screen-pos [e]
  [(.-screenX e) (.-screenY e)])

(def mousemove
  (util/event-port* js/window :mousemove))

(def mouseup
  (util/event-port* js/window :mouseup))

(ui/install-plugin! ::key (fn [el k _]
                            (set! (.-__sortable_item_key el) k)))

(defn item-key [el]
  (.-__sortable_item_key el))

(defn target [el]
  (when el
    (if (some? (item-key el))
      el
      (recur (.-parentNode el)))))

(defn initial-state [col]
  (let [id (new-uid)
        capture (frp/new-event)
        html (frp/<- (fn [col]
                       (ui/set-children col
                         (vec
                           (map-indexed (fn [i el]
                                  (-> el
                                    (ui/set-attributes (assoc (ui/attributes el) ::key [id (ui/key el)]))
                                    (ui/listen :mousedown
                                      (ui/to capture (fn [e dom-el]
                                                       [i
                                                        (mouse-pos e)
                                                        (element-pos dom-el)
                                                        (.cloneNode dom-el true)])))))
                             (ui/children col)))))
               col)]
    {:commands {:capture capture}
     :inputs {:col html}
     :id id
     :html html
     :output {:html #(frp/switch :html %)}
     :methods {:capture captured-state}}))

(defn captured-state [state [idx mouse-pos el-pos phantom]]
  (-> state
    (update-in [:commands] assoc
      :mouse mousemove
      :mouseup mouseup)
    (assoc
      :initial-state state
      :mouse-start-pos mouse-pos
      :el-start el-pos
      :methods captured-state-methods
      :html (frp/new-signal
              (-> state :values :col)))))

(def captured-state-methods
  {:mouse (fn [state e]
            (println (mouse-pos e))
            state)
   :mouseup (fn [state _]
              (:initial-state state))})

(defn sortable [col]
  (frp/<- :html
    (util/state-machine
      (initial-state col))))

(def app
  (frp/new-app))

(def images
  (DIV {:class "gallery"}
    [(for [i (range 1 11)]
       (IMG {:key i
             :src (str "http://lorempixel.com/120/120/nature/" i)
             :class "gallery-image no-drag-select"}))]))

(defn -main []
  (ui/render!
    app
    (sortable
      (frp/new-signal images))
    (js/document.getElementById "app")))
