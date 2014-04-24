(ns calculator-many.main
  (:require [calculator.main :refer [new-calc]]
            [dar.ui :refer [render!]]
            [dar.ui.frp :refer [join]])
  (:require-macros [dar.ui.frp :refer [transform]]
                   [dar.ui.dom.elements :refer [DIV]]))

(defn -main []
  (render! (transform [calcs (join (repeatedly 1000 new-calc))]
             (DIV {:id "calcs"}
               [calcs]))
           (.getElementById js/document "calcs")))
