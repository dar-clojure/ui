(ns util
  (:require [dar.ui.frp :as frp :include-macros true]))

(defn event-port [ctx type init transform]
  (frp/port (fn [push]
              (when init
                (push (init)))
              (let [handler #(when-let [v (transform %)]
                               (push v))]
                (.addEventListener ctx (name type) handler)
                (fn onkill []
                  (.removeEventListener ctx (name type) handler))))))

(defn event-port* [ctx type transform]
  (frp/as-event!
    (event-port ctx type (fn [] nil) transform)))

(defn state-machine [state]
  (let [inputs (frp/new-signal)
        commands (frp/new-signal)
        state-signal (frp/foldp (fn [state values cmds]
                                  (if-let [[k v] (first (filter (comp some? second)
                                                          cmds))]
                                    (if-let [f (get-in state [:methods k])]
                                      (-> state
                                        (assoc :values values)
                                        (f v)
                                        (dissoc :values values))
                                      state)
                                    state))
                       state
                       (frp/d-switch frp/join-map inputs)
                       (frp/d-switch* frp/join-map commands))]
    (frp/bind [out state-signal
               _ (frp/pipe inputs (frp/<- :inputs state-signal))
               _ (frp/pipe commands (frp/<- :commands state-signal))]
      out)))
