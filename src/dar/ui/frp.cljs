(ns dar.ui.frp
  (:refer-clojure :exclude [merge])
  (:require [dar.ui.frp.core :as core])
  (:require-macros [dar.ui.frp :refer [js-arguments js-array bind]]))

(defn- js-into-array [arr aseq]
  (reduce #(.push %1 %2) arr aseq))

(defn- set-value! [o v]
  (set! (.-value o) v)
  v)

(defn as-event! [s]
  (set! (.-event s) true)
  s)

(defn new-signal
  ([] (new-signal nil))
  ([initial-value]
   (core/Signal. initial-value)))

(defn new-event []
  (as-event! (new-signal)))

(defn lift [f]
  (fn []
    (core/Transform. (fn [prev args]
                       (apply f args))
      (js-arguments 0))))

;; TODO: arg dispatch is over-optimization.
;; Plain js f.apply(null, args) is smart. Need to review/patch
;; what cljs.core/apply does and see actual numbers.

(defn <-
  ([f x]
   (core/Transform. (fn [_ args]
                      (f (aget args 0)))
     (js-array x)))
  ([f x y]
   (core/Transform. (fn [_ args]
                      (f (aget args 0) (aget args 1)))
     (js-array x y)))
  ([f x y z]
   (core/Transform. (fn [_ args]
                      (f (aget args 0) (aget args 1) (aget args 2)))
     (js-array x y z)))
  ([f x y z i]
   (core/Transform. (fn [_ args]
                      (f (aget args 0) (aget args 1) (aget args 2) (aget args 3)))
     (js-array x y z i)))
  ([f x y z i j & rest]
   (core/Transform. (fn [_ args]
                      (apply f args))
     (js-into-array (js-array x y z i j) rest))))

(defn <-*
  ([f x] (as-event! (<- f x)))
  ([f x y] (as-event! (<- f x y)))
  ([f x y z] (as-event! (<- f x y z)))
  ([f x y z i] (as-event! (<- f x y z i)))
  ([f x y z i j & rest] (as-event! (apply <- f x y z i j rest))))

(defn foldp
  ([f init x]
   (doto (core/Transform. (fn [prev args]
                            (let [v (aget args 0)]
                              (if (and (.-event x) (nil? v))
                                prev
                                (f prev v))))
           (js-array x))
     (set-value! init)))

  ([f init x y]
   (doto (core/Transform. (fn [prev args]
                            (f prev (aget args 0) (aget args 1)))
           (js-array x y))
     (set-value! init)))

  ([f init x y z]
   (doto (core/Transform. (fn [prev args]
                            (f prev (aget args 0) (aget args 1) (aget args 2)))
           (js-array x y z))
     (set-value! init)))

  ([f init x y z i]
   (doto (core/Transform. (fn [prev args]
                            (f prev (aget args 0) (aget args 1) (aget args 2) (aget args 3)))
           (js-array x y z i))
     (set-value! init)))

  ([f init x y z i j & rest]
   (doto (core/Transform. (fn [prev args]
                            (.unshift args prev)
                            (apply f args))
           (js-into-array (js-array x y z i j) rest))
     (set-value! init))))

(defn merge
  ([xs]
   (core/Transform. core/mergeTransform (to-array xs)))
  ([x y]
   (core/Transform. core/mergeTransform (js-array x y)))
  ([x y z]
   (core/Transform. core/mergeTransform (js-array x y z)))
  ([x y z i]
   (core/Transform. core/mergeTransform (js-array x y z i)))
  ([x y z i j & rest]
   (core/Transform. core/mergeTransform (js-into-array (js-array x y z i j) rest))))

(defn merge*
  ([xs]
   (as-event! (merge xs)))
  ([x y]
   (as-event! (merge x y)))
  ([x y z]
   (as-event! (merge x y z)))
  ([x y z i]
   (as-event! (merge x y z i)))
  ([x y z i j & rest]
   (as-event! (core/Transform. core/mergeTransform
                (js-into-array (js-array x y z i j) rest)))))

(defn- join-fn [_ xs]
  (vec xs))

(defn join
  ([xs]
   (core/Transform. join-fn (to-array xs)))
  ([x y]
   (core/Transform. join-fn (js-array x y)))
  ([x y z]
   (core/Transform. join-fn (js-array x y z)))
  ([x y z i]
   (core/Transform. join-fn (js-array x y z i)))
  ([x y z i j & rest]
   (core/Transform. join-fn (js-into-array (js-array x y z i j) rest))))

(defn map-join [m]
  (let [ks (keys m)
        signals (vals m)]
    (core/Transform. (fn [_ vals]
                       (zipmap ks vals))
      (to-array signals))))

(defn switch [initial switch-event]
  (core/Switch. initial switch-event))

(defn port
  ([f]
   (core/Port. f))
  ([f input]
   (core/Port. f input)))

(defn port*
  ([f]
   (as-event! (port f)))
  ([f input]
   (as-event! (port f input))))

(defn pipe [target src]
  (core/Pipe. target src))

(defn pull-only [input]
  (core/PullOnly. input))

(defn hold
  ([x] (core/Hold. (js-array x)))
  ([x y] (core/Hold. (js-array x y)))
  ([x y z] (core/Hold. (js-array x y z)))
  ([x y z i] (core/Hold. (js-array x y z i)))
  ([x y z i j & rest] (core/Hold. (js-into-array (js-array x y z i j) rest))))

;
; App API
;

(defn new-app []
  (core/App.))

(defn watch! [app signal cb]
  (.watch app signal cb))

(defn push!
  ([app signal val]
   (.push app signal val)
   (.recompute app))
  ([app pushs]
   (doseq [[signal v] pushs]
     (.push app signal v))
   (.recompute app)))

;
; Misc
;

(defn object [initial-state commands commands-signal]
  (foldp (fn [state [cmd & args]]
           (apply (commands cmd) state args))
    initial-state
    commands-signal))
