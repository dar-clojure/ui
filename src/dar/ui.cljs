(ns dar.ui
  (:require [dar.ui.dom :as dom]
            [dar.ui.frp :as frp]))

(def ^:dynamic *app* nil)

(defn new-app []
  (atom (frp/->App {} nil #{})))

(defn push! [app signal val]
  (swap! app frp/push signal val)
  nil)

(defn pull! [app signal]
  (let [[v state] (frp/pull @app signal)]
    (reset! app state)
    v))

(defn probe [app signal]
  (frp/probe @app signal))

(defn to
  ([signal] (to signal nil))
  ([signal proc]
   (fn [app val]
     (let [val (cond (fn? proc) (proc val)
                     (nil? proc) val
                     :else proc)]
       (when-not (nil? val)
         (push! app signal val))))))

(defn render!
  ([main el] (render! (new-app) main el))
  ([app main el]
   (pull! app main)
   (let [el (binding [*app* app]
              (dom/update-element! (probe app main) nil el))]
     (add-watch app (gensym) (fn [_ _ prev-state new-state]
                               (let [prev-dom (frp/probe prev-state main)
                                     new-dom (frp/probe new-state main)]
                                 (binding [*app* app]
                                   (dom/update-element! new-dom prev-dom el)))))
     app)))
