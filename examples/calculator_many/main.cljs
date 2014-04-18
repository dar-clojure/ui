(ns calculator-many.main
  (:require [calculator.main :refer [new-calc]]
            [dar.ui :refer [render!]]
            [dar.ui.frp :refer [join]])
  (:require-macros [dar.ui.macro :refer [transform DIV]]))

(defn -main []
  (render! (transform [calcs (apply join (repeatedly 1000 new-calc))]
             (DIV {:id "calcs"}
               [calcs]))
           (.getElementById js/document "calcs")))
