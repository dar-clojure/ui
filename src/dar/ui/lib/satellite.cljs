(ns dar.ui.lib.satellite
  "Port of https://github.com/jkroso/satellite"
  (:require  [dar.ui :as ui]
             [dar.ui.dom :as dom]
             [dar.ui.lib.dims :as dims]))

(defn align [type [x1 x2] [y1 y2]]
  (let [ly (- y2 y1)]
    (case type
      :bb [x1 (+ x1 ly)]
      :be [(- x1 ly) x1]
      :eb [x2 (+ x2 ly)]
      :ee [(- x2 ly) x2]
      :center (let [lx (- x2 x1)]
                [(+ x1 (/ (- lx ly) 2))
                 (+ x1 (/ (+ lx ly) 2))]))))

(def positions {:top-right [:bb :be]
                :top [:center :be]
                :top-left [:ee :be]
                :left-top [:eb :bb]
                :left [:eb :center]
                :left-bottom [:eb :ee]
                :bottom-left [:ee :eb]
                :bottom [:center :eb]
                :bottom-right [:bb :eb]
                :right-bottom [:be :ee]
                :right [:be :center]
                :right-top [:be :bb]})

(defn position [o s pos]
  (mapv align (positions pos) (dims/dims o) (dims/dims s)))

(defn position! [o s pos]
  (let [box (position (dims/position o) s pos)]
    (set! (.. s -style -top) (str (dims/top box) "px"))
    (set! (.. s -style -left) (str (dims/left box) "px"))))

(defn run-reposition-loop [o s]
  (if (dom/in-dom? o)
    (when (dom/in-dom? s)
      (position! o s (dom/data s ::pos))
      (dom/raf #(run-reposition-loop o s)))
    (dom/remove! s)))

(defn update! [o el pos new old]
  (when-let [s (ui/update! el new old)]
    (dom/set-data! s ::pos pos)
    (when-not (identical? s el)
      (dom/tick #(when-let [parent (.-parentNode o)]
                   (dom/add-attribute! s "data-virtual")
                   (set! (.. s -style -position) "absolute")
                   (.appendChild parent s)
                   (run-reposition-loop o s))))
    s))

(ui/install-plugin! :satellite (fn [o [pos new] [_ old]]
                                 (dom/set-data! o ::satellite
                                   (if (or new old)
                                     (update! o (dom/data o ::satellite) pos new old)))))
