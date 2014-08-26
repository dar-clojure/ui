(ns sortable.main
  (:require [dar.ui.frp :as frp :include-macros true]))

(defn event-port [ctx type transform]
  (frp/port (fn [push]
              (let [handler (comp push transform)]
                (.addEventListener ctx (name type) handler)
                (fn onkill []
                  (.removeEventListener ctx (name type) handler))))))

(def mouse-position
  (event-port js/window :mousemove
    (fn [e]
      {:screenX (.-screenX e)
       :screenY (.-screenY e)})))



(defn measure [x y]
  (let [el (js/document.getElementById "tile")]
    (set! (.. el -style -display) "none")
    (js/console.log (js/document.elementFromPoint x y))
    (set! (.. el -style -display) nil)))

(defn -main []
  (.addEventListener js/document "mousemove"
    (fn [e]
      (measure (.-screenX e) (.-screenY e)))))
