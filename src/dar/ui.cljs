(ns dar.ui
  (:require [dar.ui.dom :as dom]
            [dar.ui.frp :refer [probe]]
            [dar.ui.frp.app :refer [pull! push! watch! new-app]]))

(defn render!
  ([main el] (render! (new-app) main el))
  ([app main el]
   (let [html (pull! app main)
         fire! (partial push! app)
         el (binding [dom/*fire* fire!]
              (dom/update-element! html nil el))]
     (watch! app (fn [new old]
                   (let [new-html (probe new main)
                         old-html (probe old main)]
                     (binding [dom/*fire* fire!]
                       (dom/update-element! new-html old-html el)))))
     app)))
