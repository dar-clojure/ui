(ns dar.ui.frp
  (:require [dar.ui.frp.core :as core])
  (:require-macros [dar.ui.frp :refer [js-arguments]]))

(defn new-signal
  ([] (new-signal nil))
  ([initial-value]
   (core/Signal. initial-value)))

(defn new-event []
  (doto (new-signal)
    (.asEvent)))

(defn lift [f]
  (fn []
    (core/Transform (fn [prev, args]
                      (.apply f nil args))
      (js-arguments 0))))

(defn foldp
  ([f x]
   (core/Transform (fn [prev [x]]
                     (f prev x))
     (array x)))
  ([f x y]
   (core/Transform (fn [prev [x y]]
                     (f prev x y))
     (array x y)))
  ([f x y z]
   (core/Transform (fn [prev [x y z]]
                     (f prev x y z))
     (array x y z)))

  ([f x y z i & args]
   (core/Transform (fn [prev args]
                     (.unshift args prev)
                     (.apply f nil args))
     (js-arguments 1))))

(defn join
  ([xs]
   (core/Transform (fn [_ xs]
                     (vec xs))
     (to-array xs)))
  ([_ _ & _]
   (core/Transform (fn [_ xs]
                     (vec xs))
     (js-arguments 0))))

(defn merge
  ([xs]
   (core/Transform core/mergeTransform (to-array xs)))
  ([_ _ & _]
   (core/Transform core/mergeTransform (js-arguments 0))))

(defn automaton [initial-state commands commands-signal]
  (foldp (fn [state [cmd & args]]
           (apply (commands cmd) state args))
    initial-state
    commands-signal))

(defn new-app []
  (core/App.))

(defn watch! [app signal cb]
  (.watch app signal cb))

(defn push! [app signal val]
  (.push app signal val))
