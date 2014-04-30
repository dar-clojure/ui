(ns dar.ui.dom.satellite
  "Port of https://github.com/jkroso/satellite"
  (:require [dar.ui.dom.util :as dom]
            [dar.ui.dom.core :as dom-core]))

(defrecord Box [top left right bottom])

(defn dx [box]
  [(:left box) (:right box)])

(defn dy [box]
  [(:top box) (:bottom box)])

(defn xy->box [[left right] [top bottom]]
  (->Box top left right bottom))

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

(def positions {:north-east [:bb :be]
                :north [:center :be]
                :north-west [:ee :be]
                :west-north [:eb :bb]
                :west [:eb :center]
                :west-south [:eb :ee]
                :south-west [:ee :eb]
                :south [:center :eb]
                :south-east [:bb :eb]
                :east-south [:be :ee]
                :east [:be :center]
                :east-north [:be :bb]})

(defn place [t o s]
  (let [[tx ty] (positions t)]
    (xy->box (align tx (dx o) (dx s))
             (align ty (dy o) (dy s)))))

(defn within-segment? [[x1 x2] [c1 c2]]
  (and (>= c2 x2)
       (>= x2 c1)
       (>= x1 c1)
       (>= c2 x1)))

(defn within? [box container]
  (and (within-segment? (dx container) (dx box))
       (within-segment? (dy container) (dy box))))
