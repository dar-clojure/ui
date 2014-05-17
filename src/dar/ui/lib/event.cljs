(ns dar.ui.lib.event
  (:require [dar.ui.frp :as frp]
            [dar.ui.dom.util :as dom]))

(defn signal
  ([el event] (signal el event identity))
  ([el event proc] (signal el event proc nil))
  ([el event proc value]
   (frp/port (fn [push!]
               (let [handler (fn [e]
                               (when-let [v (proc e)]
                                 (push! e)))]
                 (dom/listen! el event handler)
                 {:dispose (dom/unlisten! el event handler)
                  :value value})))))

(defn event [& args]
  (frp/as-event (apply signal args)))

(defn once ; TODO: this is a temp hack
  ([el event] (once el event identity))
  ([el event proc] (once el event proc nil))
  ([el event proc value]
   (frp/event-port (fn [push!]
                     (let [handler (fn [e]
                                     (when-let [v (proc e)]
                                       (push! e)
                                       (push! nil)))]
                       (dom/listen! el event handler)
                       {:dispose (dom/unlisten! el event handler)
                        :value value})))))
