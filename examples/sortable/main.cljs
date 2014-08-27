(ns sortable.main
  (:require [dar.ui.frp :as frp :include-macros true]))

(defn event-port [ctx type init transform]
   (frp/port (fn [push]
               (when init
                 (push (init)))
               (let [handler (comp push transform)]
                 (.addEventListener ctx (name type) handler)
                 (fn onkill []
                   (.removeEventListener ctx (name type) handler))))))

(defn mouse-event-pos [e]
  [(.-screenX e) (.-screenY e)])

(def mouse-position
  (event-port js/window :mousemove
    (fn [] [0 0])
    mouse-event-pos))

(def mouseup
  (event-port* js/window :mouseup
    (constantly true)))

(defn initial-state [col]
  (let [capture (new-event)
        html (frp/<- (fn [col]
                       (set-children col
                         (map (fn [el]
                                (listen el :mousedown
                                  (to capture (fn [e]
                                                [(key el) (mouse-event-pos e)]))))
                           (children col))))
               col)]
    {:commands {:capture capture}
     :inputs {:col html}
     :html html
     :methods {:capture (fn [state k]
                          (captured-state state k))}}))

(defn captured-state [state [k pos]]
  (-> state
    (update-in [:commands] assoc
      :mouse mouse-position
      :mouseup mouseup)
    (assoc
      :start pos
      :initial-state state
      :methods captured-state-methods
      :html (new-signal
              (prevent-mouse-events (-> state :signals :col))))))

(def captured-state-methods
  {:mouse (fn [state pos]
            )
   :mouseup (fn [state _]
              (:initial-state state))})
