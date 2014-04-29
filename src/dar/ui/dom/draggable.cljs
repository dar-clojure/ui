(ns dar.ui.dom.draggable
  (:require [dar.ui.dom.util :as dom]))

(defn position [el]
  [(.-offsetLeft el) (.-offsetTop el)])

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

(defn watch! [el cb]
  (let [drag-events {:mousemove (fn [e]
                                  (cb :drag-move (event el e)))
                     :mouseup (fn [e]
                                (dom/cleanup! el ::drag-events)
                                (cb :drag-end (event el e)))}
        start-events {:mousedown (fn [e]
                                   (when (= 0 (.-button e))
                                     (dom/stop! e)
                                     (dom/set-data! el ::start (position el))
                                     (dom/set-data! el ::mouse-start [(.-clientX e) (.-clientY e)])
                                     (dom/listen! js/window drag-events)
                                     (dom/set-data! el ::drag-events #(dom/unlisten! js/window drag-events))
                                     (cb :drag-start (event el e))))}]
    (dom/listen! el start-events)
    (dom/set-data! el ::start-events #(dom/unlisten! el start-events))))

(defn unwatch! [el]
  (dom/cleanup! el ::start-events)
  (dom/cleanup! el ::drag-events))

(defn place! [el [x y]]
  (let [s (.-style el)]
    (set! (.-position s) "absolute")
    (set! (.-left s) (str x "px"))
    (set! (.-top s) (str y "px"))))

(defn unplace! [el]
  (set! (.. el -style -position) "")) ;; TODO: be smarter

(defn vlen [[x y]]
  (js/Math.sqrt (+ (* x x) (* y y))))

(defn dragging? [el]
  (.hasAttribute el "data-dragging"))

(defn handler [el stickiness cb]
  (fn [t e]
    (when (= :drag-start t)
      (dom/add-attribute! el "data-drag-start"))
    (when (= :drag-end t)
      (dom/remove-attribute! el "data-drag-start")
      (when (dragging? el)
        (dom/remove-attribute! el "data-dragging")
        (cb :drag-end e)))
    (when (= :drag-move t)
      (if (dragging? el)
        (cb :drag-move e)
        (when (>= (vlen (:move e)) stickiness)
          (dom/add-attribute! el "data-dragging")
          (cb :drag-start e))))))

(defn place-handler [el cb]
  (fn [t e]
    (when (#{:drag-start :drag-move} t)
      (place! el (:end e)))
    (when (= :drag-end t)
      (unplace! el))
    (cb t e)))

(defn new-phantom! [el pos]
  (let [p (.cloneNode el true)]
    (dom/add-attribute! p "data-drag-phantom")
    (dom/add-attribute! p "data-virtual")
    (place! p pos)
    (dom/set-data! el ::phantom p)
    (.appendChild js/document.body p)
    p))

(defn phantom-handler [el cb]
  (fn [t {pos :end :as e}]
    (when (= :drag-start t)
      (new-phantom! el pos))
    (when (= :drag-move t)
      (place! (dom/data el ::phantom) pos))
    (when (= :drag-end t)
      (let [p (dom/data el ::phantom)]
        (dom/set-data! el ::phantom nil)
        (dom/add-attribute! p "data-deleted")
        (dom/tick #(place! p (position el)))
        (js/setTimeout #(dom/remove! p) 500)))
    (cb t e)))

(defn noop-handler [_ _])

(defn init! [el {:keys [stickiness phantom]}]
  (watch! el (handler el (or stickiness 0)
                      (if phantom
                        (phantom-handler el noop-handler)
                        (place-handler el noop-handler)))))

(def plugin
  {:on (fn [el opts]
         (when opts
           (init! el (if (true? opts)
                       {}
                       opts))))})
