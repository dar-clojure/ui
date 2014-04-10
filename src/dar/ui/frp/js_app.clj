(ns dar.ui.frp.js-app
  (:require [dar.ui.frp :as frp]))

(defn new-app []
  (atom (frp/->App {} () #{})))

(defn push! [app signal val]
  (swap! app frp/push signal val)
  nil)

(defn probe [app signal]
  (let [[v new-app] (frp/probe @app signal)]
    (reset! app new-app)
    v))
