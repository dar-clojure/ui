(ns dar.ui
  (:require [dar.ui.dom :as dom]
            [dar.ui.frp :as frp]))

(def ^:dynamic *app* nil)

(defn to
  ([signal] (to signal nil))
  ([signal proc]
   (fn [app val]
     (let [val (cond (fn? proc) (proc val)
                     (nil? proc) val
                     :else proc)]
       (when-not (nil? val)
         (frp/push! app signal val))))))

(defn render!
  ([main el] (render! (frp/new-app) main el))
  ([app main el]
   (let [html (frp/pull! app main)
         el (binding [*app* app]
              (dom/update-element! html nil el))]
     (frp/watch! app (fn [new old]
                       (let [new-html (frp/probe new main)
                             old-html (frp/probe old main)]
                         (binding [*app* app]
                           (dom/update-element! new-html old-html el)))))
     app)))
