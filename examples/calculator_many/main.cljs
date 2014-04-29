(ns calculator-many.main
  (:require [calculator.main :refer [new-calc]]
            [dar.ui :as ui]
            [dar.ui.frp :refer [join]])
  (:require-macros [dar.ui.frp :refer [transform]]
                   [dar.ui.dom.elements :refer [DIV]]))

(defn -main []
  (ui/render! (transform [calcs (join (repeatedly 1000 new-calc))]
                (DIV {:id "calcs"}
                  [calcs]))
              (.getElementById js/document "calcs")))
