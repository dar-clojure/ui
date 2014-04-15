(ns dar.ui.app
  (:require [dar.ui.dom :as dom]
            [dar.ui.frp :as frp]))

(defn push! [app signal val]
  (swap! app frp/push signal val)
  nil)

(defn pull! [app signal]
  (let [[v state] (frp/pull @app signal)]
    (reset! app state)
    v))

(defn probe [app signal]
  (frp/probe @app signal))

(defn alive? [app signal]
  (frp/alive? @app signal))

(defn new-app []
  (reify (atom (frp/->App {} nil #{}))
    dom/IListener
    (push! [this signal val] (push! this signal val))
    (alive? [this signal] (alive? this signal))))

(defn render!
  ([main el] (render! (new-app) main el))
  ([app main el]
   (pull! app main)
   (binding [dom/*listener* app]
     (dom/update-element! (probe app main) nil el))
   (add-watch app (gensym) (fn [_ _ prev-state new-state]
                             (let [prev-dom (frp/probe prev-state main)
                                   new-dom (frp/probe new-state main)]
                               (binding [dom/*listener* app]
                                 (dom/update-element! new-dom prev-dom el)))))
   app))
