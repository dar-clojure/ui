(ns dar.ui.frp
  (:refer-clojure :exclude [merge])
  (:require [dar.ui.frp.core :as core])
  (:require-macros [dar.ui.frp :refer [js-arguments]]))

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
                       (.apply f nil args))
      (js-arguments 0))))

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
                            (.apply f nil args))
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

(defn automaton [initial-state commands commands-signal]
  (foldp (fn [state [cmd & args]]
           (apply (commands cmd) state args))
    initial-state
    commands-signal))

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
     (.push app signal val))
   (.recompute app)))
