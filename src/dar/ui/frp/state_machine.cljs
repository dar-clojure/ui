(ns dar.ui.frp.state-machine
  (:require [dar.ui.frp :refer [<- <-* new-signal foldp map-join pull-only switch d-switch* push]]))

(defn- dispatch-commands [state cmds values]
  (when-let [[[k v]] (filter (comp some? second) cmds)]
    (when-let [f (get-in state [:methods k])]
      (-> state
        (assoc :values values)
        (f v)
        (dissoc :values values)))))

(defn- update-output-signals [prev state state-signal]
  (let [old (-> prev meta :constructors)
        new (-> state :output)]
    (if (identical? old new)
      prev
      (with-meta
        (into {}
          (map (fn [[k f]]
                 (if (identical? f (get old k))
                   [k (get prev k)]
                   [k (f state-signal)]))
            new))
        {:constructors new}))))

(defn create [state]
  (let [inputs (new-signal (:inputs state))
        commands (new-signal (:commands state))
        state-signal (new-signal state)
        state-signal* (<-* dispatch-commands
                        (pull-only state-signal)
                        (d-switch* map-join commands)
                        (pull-only (switch map-join inputs)))
        pushs (push
                (<-* (fn [state]
                       (when state
                         (-> (or (:push state) [])
                           (conj [inputs (:inputs state)])
                           (conj [commands (:commands state)])
                           (conj [state-signal (dissoc state :push)]))))
                  state-signal*))
        lowered-state-signal (->> state-signal
                               (<- (fn [_ s] s) pushs)
                               (<- identity) ; output switch level
                               (<- identity))
        output-signals (foldp (fn [signals state _]
                                (update-output-signals signals state lowered-state-signal))
                         nil
                         state-signal
                         pushs)]
    (switch map-join output-signals)))
