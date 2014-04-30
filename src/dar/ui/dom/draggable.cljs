(ns dar.ui.dom.draggable
  (:require [dar.ui.dom.util :as dom]))

(defn- event [el e]
  (let [start (dom/data el ::mouse-start)
        pos [(.-clientX e) (.-clientY e)]
        move (mapv - pos start)]
    {:mouse-start mouse-start
     :mouse-pos pos
     :mouse-move move}))

(defn watch!
  ([el cb] (watch! el nil cb))
  ([el handle cb]
   (let [drag-events {:mousemove (fn [e]
                                   (cb :drag-move (event el e)))
                      :mouseup (fn [e]
                                 (dom/cleanup! el ::drag-events)
                                 (cb :drag-end (event el e)))}
         start-events {:mousedown (fn [e]
                                    (when (and (= 0 (.-button e))
                                               (or (not handle)
                                                   (dom/child? (.-target e) (dom/get el handle))))
                                      (dom/stop! e)
                                      (dom/set-data! el ::mouse-start [(.-clientX e) (.-clientY e)])
                                      (dom/listen! js/window drag-events)
                                      (dom/set-data! el ::drag-events #(dom/unlisten! js/window drag-events))
                                      (cb :drag-start (event el e))))}]
     (dom/listen! el start-events)
     (dom/set-data! el ::start-events #(dom/unlisten! el start-events)))))

(defn unwatch! [el]
  (dom/cleanup! el ::start-events)
  (dom/cleanup! el ::drag-events))

(defn place! [el [x y]]
  (let [s (.-style el)]
    (set! (.-position s) "absolute")
    (set! (.-left s) (str x "px"))
    (set! (.-top s) (str y "px"))))

(defn vlen [[x y]]
  (js/Math.sqrt (+ (* x x) (* y y))))

(defn dragging? [el]
  (.hasAttribute el "data-dragging"))

(defn main-handler
  ([el cb] (main-handler el 0 cb))
  ([el stickiness cb]
   (fn [t e]
     (when (= :drag-start t)
       (dom/add-attribute! el "data-draggable-captured"))
     (when (= :drag-end t)
       (dom/remove-attribute! el "data-draggable-captured")
       (when (dragging? el)
         (dom/remove-attribute! el "data-dragging")
         (cb :drag-end e)))
     (when (= :drag-move t)
       (if (dragging? el)
         (cb :drag-move e)
         (when (>= (vlen (:mouse-move e)) stickiness)
           (dom/add-attribute! el "data-dragging")
           (cb :drag-start e)))))))

(defn position [el]
  [(.-offsetLeft el) (.-offsetTop el)])

(defn place-handler [el cb]
  (fn [t e]
    (when (= :drag-start t)
      (dom/set-data! el ::start (position el)))
    (let [start (dom/data el ::start)
          move (:mouse-move e)
          pos (mapv + start move)]
      (place! el pos)
      (cb t (assoc e :pos pos :start start)))))

(defn init-plugin! [el opts]
  (let [[handle stickiness] (if (map? opts)
                              [(:handle opts) (:stickiness opts)])]
    (->> (place-handler el (fn [_ _]))
         (main-handler el stickiness)
         (watch! el handle))))

(def plugin
  {:on (fn [el opts]
         (when opts
           (init-plugin! el opts)))

   :update (fn [el new old]
             (when-not new
               (unwatch! el))
             (when (and new (not old))
               (init-plugin! el new))
             (when (and new old)
               (js/console.warn "Changes for properties of draggable are not supported")))

   :off (fn [el _]
          (unwatch! el))})
