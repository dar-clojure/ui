(ns dar.ui.dom.draggable
  (:require [dar.ui.dom.util :as dom]))

(defn- event [el e]
  (let [start (dom/data el ::start)
        mouse-start (dom/data el ::mouse-start)
        mouse [(.-clientX e) (.-clientY e)]
        move (mapv - mouse mouse-start)
        end (mapv + start move)]
    {:start start
     :mouse-start mouse-start
     :mouse mouse
     :move move
     :end end}))

(defn bind! [el cb]
  (let [g-events {:mousemove (fn [e]
                               (cb :drag (event el e)))
                  :mouseup (fn [e]
                             (dom/cleanup! el ::unlisten-global)
                             (cb :drag-end (event el e)))}
        l-events {:mousedown (fn [e]
                               (when (= 0 (.-button e))
                                 (dom/set-data! el ::start [(.-offsetLeft el) (.-offsetTop el)])
                                 (dom/set-data! el ::mouse-start [(.-clientX e) (.-clientY e)])
                                 (dom/listen! js/window g-events)
                                 (dom/set-data! el ::unlisten-global #(dom/unlisten! js/window g-events))
                                 (cb :drag-start (event el e))))}]
    (dom/listen! el l-events)
    (dom/set-data! el ::unlisten-local #(dom/unlisten! el l-events))))

(defn unbind! [el]
  (dom/cleanup! el ::unlisten-local)
  (dom/cleanup! el ::unlisten-global))

(defn place! [el [x y]]
  (let [s (.-style el)]
    (set! (.-position s) "absolute")
    (set! (.-left s) (str x "px"))
    (set! (.-top s) (str y "px"))))

(defn unplace! [el]
  (set! (.. el -style -position) nil))

(defn vlen [[x y]]
  (js/Math.sqrt (+ (* x x) (* y y))))

(defn dragging? [el]
  (.hasAttribute el "data-dragging"))

(defn init! [el]
  (bind! el (fn [e {pos :end move :move}]
              (when (= :drag-start e)
                (.setAttribute el "data-dragging" ""))
              (when (= :drag-end e)
                (.removeAttribute el "data-dragging")
                (unplace! el))
              (place! el pos))))

(def plugin
  {:on (fn [el _]
         (init! el))})
