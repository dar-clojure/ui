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
    (core/Transform. (fn [prev, args]
                       (apply f args))
      (js-arguments 0))))

;; TODO: arg dispatch is likely to be an overoptimization.
;; Plain js f.apply(null, args) is smart. Need to review
;; what cljs (apply ..) does and see actual numbers.

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

(defn switch
  ([input] (core/Switch. input))
  ([f x] (switch (<- f x)))
  ([f x y] (switch (<- f x y)))
  ([f x y z] (switch (<- f x y z)))
  ([f x y z i] (switch (<- f x y z i)))
  ([f x y z i j & rest] (switch (apply <- f x y z i j rest))))

(defn switch*
  ([input] (as-event! (switch input)))
  ([f x] (as-event! (switch f x)))
  ([f x y] (as-event! (switch f x y)))
  ([f x y z] (as-event! (switch f x y z)))
  ([f x y z i] (as-event! (switch f x y z i)))
  ([f x y z i j & rest] (as-event! (apply switch f x y z i j rest))))

(defn d-switch
  "
  (d-switch ..) is like a (switch ..), except it doesn't
  notify listeners about value change upon switching to another signal.
  Useful for breaking cycles.
  "
  ([input] (core/DSwitch. input))
  ([f x] (d-switch (<- f x)))
  ([f x y] (d-switch (<- f x y)))
  ([f x y z] (d-switch (<- f x y z)))
  ([f x y z i] (d-switch (<- f x y z i)))
  ([f x y z i j & rest] (d-switch (apply <- f x y z i j rest))))

(defn d-switch*
  ([input] (as-event! (d-switch input)))
  ([f x] (as-event! (d-switch f x)))
  ([f x y] (as-event! (d-switch f x y)))
  ([f x y z] (as-event! (d-switch f x y z)))
  ([f x y z i] (as-event! (d-switch f x y z i)))
  ([f x y z i j & rest] (as-event! (apply d-switch f x y z i j rest))))

(set! (.-recompute core/ASignalsMap.prototype)
  (fn []
    (this-as this
      (let [spec (.. this -spec)
            sf (.. spec -sf)
            input (.. this -input)
            new-m (.. input -value)
            old-m (.. this -oldInputValue)
            signals-m (.. this -value)]
        (set! (.. this -oldInputValue) new-m)
        (set! (.. this -value)
          (loop [new new-m
                 sm signals-m
                 signals (seq signals-m)]
            (if-let [[k] (first signals)]
              (if (contains? new k)
                (recur (dissoc new k) sm (next signals))
                (recur new (dissoc sm k) (next signals)))
              (reduce (fn [sm [k]]
                        (let [in (bind [m input]
                                   [k (get m k)])]
                          (assoc sm k (sf in))))
                sm
                new))))))))

(defn map-switch [sf input]
  (switch map-join
    (core/SignalsMap. input sf)))

(defn port [f]
  (core/Port. f))

(defn port* [f]
  (as-event! (port f)))

(set! (.-recompute core/APush.prototype)
  (fn []
    (this-as this
      (doseq [[s v] (.. this -input -value)]
        (.. this -app (push s v))))))

(defn push [src]
  (core/Push. src))

(defn pipe [target src]
  (push (<- (fn [val]
              [[target val]])
          src)))

(defn pull-only [input]
  (core/PullOnly. input))

(defn effect [f kill x]
  (core/Effect. f kill (js-arguments 2)))

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

(defn automaton [initial-state commands commands-signal]
  (foldp (fn [state [cmd & args]]
           (apply (commands cmd) state args))
    initial-state
    commands-signal))
