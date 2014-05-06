(ns dar.ui.dom.satellite
  "Port of https://github.com/jkroso/satellite"
  (:require  [dar.ui.dom.core :as dom-core]
             [dar.ui.dom.util :as dom]
             [dar.ui.dom.util.dims :refer [dims top left]]))

(defn align [type [x1 x2] [y1 y2]]
  (let [ly (- y2 y1)]
    (case type
      :bb [x1 (+ x1 ly)]
      :be [(- x1 ly) x1]
      :eb [x2 (+ x2 ly)]
      :ee [(- x2 ly) x2]
      :center (let [lx (- x2 x1)]
                [(/ (- lx ly) 2)
                 (/ (+ lx ly) 2)]))))

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

(defn place [t o s]
  (mapv align (positions t) (dims o) (dims s)))

(defn plugin [o new old]
  (let [new-el (:el new)
        old-el (:el old)
        p (:position new)
        s (dom/data o ::satellite)]
    (when-not new-el
      (when old-el
        (dom-core/remove! old-el s))
      (dom/set-data! o ::satellite nil))
    (when new-el
      (let [s (dom/update-element! new-el old-el (dom/data o ::satellite))]
        (dom/set-data! o ::satellite s)
        (dom/add-attribute! s "data-virtual")
        (dom/tick #(do
                     (.appendChild js/document.body s)
                     (let [box (place p o s)]
                       (set! (.. s -style -position) "absolute")
                       (set! (.. s -style -top) (top box))
                       (set! (.. s -style -left) (left box)))))))))
