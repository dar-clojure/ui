(ns calculator-many.main
  (:require [calculator.main :refer [new-calc]]
            [dar.ui :as ui]
            [dar.ui.frp :as frp :include-macros true])
  (:require-macros [dar.ui.html :refer [DIV]]))

(defn -main []
  (ui/render! (frp/transform [calcs (frp/join (repeatedly 1000 new-calc))]
                (DIV {:id "calcs"}
                  [calcs]))
              (.getElementById js/document "calcs")))
