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

(defn event-port*
  ([ctx type] (event-port* ctx type identity))
  ([ctx type transform]
   (frp/as-event!
     (event-port ctx type (fn [] nil) transform))))
