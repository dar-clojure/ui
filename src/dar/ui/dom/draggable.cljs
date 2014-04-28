(ns dar.ui.dom.draggable
  (:require [dar.ui.dom.util :as dom]))

(defn- ev-position [el e]
  (let [start (dom/data el ::start)
        mouse-start (dom/data el ::mouse-start)
        mouse [(.-clientX e) (.-clientY e)]]
    (mapv + start (map - mouse mouse-start))))

(defn bind! [el cb]
  (let [g-events {:mousemove (fn [e]
                               (cb :drag (ev-position el e)))
                  :mouseup (fn [e]
                             (dom/cleanup! el ::unlisten-global)
                             (cb :drag-end (ev-position el e)))}
        l-events {:mousedown (fn [e]
                               (let [pos [(.-offsetLeft el) (.-offsetTop el)]]
                                 (dom/set-data! el ::start pos)
                                 (dom/set-data! el ::mouse-start [(.-clientX e) (.-clientY e)])
                                 (dom/listen! js/window g-events)
                                 (dom/set-data! el ::unlisten-global #(dom/unlisten! js/window g-events))
                                 (cb :drag-start pos)))}]
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

(def plugin
  {:on (fn [el _]
         (bind! el (fn [event pos]
                     (place! el pos))))})
