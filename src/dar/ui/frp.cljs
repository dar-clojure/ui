(ns dar.ui.frp
  (:refer-clojure :exclude [merge])
  (:require [dar.ui.frp.core :as core])
  (:require-macros [dar.ui.frp :refer [js-arguments bind]]))

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
    (core/Transform. (fn [prev, args]
                       (apply f args))
      (js-arguments 0))))

(defn <-
  ([f x]
   (core/Transform. (fn [_ [v]]
                      (f v))
     (js-arguments 1)))
  ([f x y]
   (core/Transform. (fn [_ [vx vy]]
                      (f vx vy))
     (js-arguments 1)))
  ([f x y z & args]
   (core/Transform. (fn [_ args]
                      (apply f args))
     (js-arguments 1))))

(defn foldp
  ([f init x]
   (doto (core/Transform. (fn [prev [v]]
                            (if (and (.-event x) (nil? v))
                              prev
                              (f prev v)))
           (js-arguments 2))
     (set-value! init)))

  ([f init x y]
   (doto (core/Transform. (fn [prev [x y]]
                            (f prev x y))
           (js-arguments 2))
     (set-value! init)))

  ([f init x y z]
   (doto (core/Transform. (fn [prev [x y z]]
                            (f prev x y z))
           (js-arguments 2))
     (set-value! init)))

  ([f init x y z i & args]
   (doto (core/Transform. (fn [prev args]
                            (.unshift args prev)
                            (apply f args))
           (js-arguments 2))
     (set-value! init))))

(defn join
  ([xs]
   (core/Transform. (fn [_ xs]
                      (vec xs))
     (to-array xs)))
  ([_ _ & _]
   (core/Transform. (fn [_ xs]
                      (vec xs))
     (js-arguments 0))))

(defn merge
  ([xs]
   (core/Transform. core/mergeTransform (to-array xs)))
  ([_ _ & _]
   (core/Transform. core/mergeTransform (js-arguments 0))))

(defn merge*
  ([xs]
   (as-event! (core/Transform. core/mergeTransform (to-array xs))))
  ([_ _ & _]
   (as-event! (core/Transform. core/mergeTransform (js-arguments 0)))))

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

(set! (.-recompute core/AMapSwitch.prototype)
  (fn []
    (this-as this
      (let [app (.. this -app)
            spec (.. this -spec)
            sf (.. this -sf)
            joiner (.. this -joiner)
            new-m (.. this -input -value)
            old-m (.. this -value)
            sm (or (.. this -signals) {})]
        (set! (.-value this) new-m)
        (loop [new (transient new-m)
               m (transient sm)
               signals (seq sm)]
          (if-let [[k out] (first signals)]
            (if (contains? new k)
              (recur (dissoc! new k) m (next signals))
              (do
                (.remove joiner out)
                (recur new (dissoc! m k) (next signals))))
            (set! (.-signals this)
              (persistent!
                (reduce (fn [m [k v]]
                          (let [in (bind [m spec]
                                     [k (get m k)])
                                out (sf in)]
                            (.add joiner k out)
                            (assoc! m k out)))
                  m
                  (persistent! new))))))))))

(defn map-switch
  ([sf input] (map-switch sf identity input))
  ([sf post input]
   (core/Joiner.
     (core/MapSwitch. input sf)
     post)))

(defn switch
  ([input] (core/Switch. input))
  ([f x] (switch (<- f x)))
  ([f x y] (switch (<- f x y)))
  ([f x y z & args] (switch (apply <- f x y z args))))


(defn automaton [initial-state commands commands-signal]
  (foldp (fn [state [cmd & args]]
           (apply (commands cmd) state args))
    initial-state
    commands-signal))
