(ns dar.ui.frp
  (:require [dar.ui.frp.core :as core]))

(defn new-app []
  (atom (core/->App {} {} nil #{})))

(defn push! [app signal val]
  (swap! app core/push signal val)
  nil)

(defn pull! [app signal]
  (let [[v state] (core/pull @app signal)]
    (reset! app state)
    v))

(defn watch! [app cb]
  (add-watch app (gensym) (fn [_ _ old new]
                            (cb new old))))

(def probe core/probe)
(def lift core/lift)
(def switch core/lift)
(def foldp core/foldp)
(def join core/join)
(def new-signal core/new-signal)
(def new-event core/new-event)
(def as-event core/as-event)
