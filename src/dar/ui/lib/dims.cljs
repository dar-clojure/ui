(ns dar.ui.util.dims)

(defprotocol IBox
  (dims [this]))

(defrecord Box [top left right bottom]
  IBox
  (dims [_] [[left right] [top bottom]]))

(defn rect->dims [r]
  [[(.-left r) (.-right r)] [(.-top r) (.-bottom r)]])

(extend-protocol IBox
  PersistentVector
  (dims [this] this)

  js/Element
  (dims [el] (rect->dims (.getBoundingClientRect el))))

(defn top [b]
  (-> b dims second first))

(defn left [b]
  (-> b dims first first))

(defn within-segment? [[x1 x2] [c1 c2]]
  (and (>= c2 x2)
       (>= x2 c1)
       (>= x1 c1)
       (>= c2 x1)))

(defn within? [box container]
  (every? true? (map within-segment? (dims box) (dims container))))
