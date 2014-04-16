(ns calculator.main
  (:refer-clojure :exclude [key keys])
  (:require [dar.ui :refer [render!]]
            [dar.ui.frp :refer [new-signal]])
  (:require-macros [dar.ui.macro :refer [TABLE TBODY TD TR DIV]]))

(defn key
  ([label command] (key label command 1))
  ([label command span]
   (TD {:colspan span}
     (DIV {:id (-> command first name)}
       label))))

(def keys [(TR nil
             (key "AC" [:ac])
             (key "MS" [:ms])
             (key "MR" [:mr])
             (key "÷" [:div]))
           (TR nil
             (key "7" [:num 7])
             (key "8" [:num 8])
             (key "9" [:num 9])
             (key "×" [:mult]))
           (TR nil
             (key "4" [:num 4])
             (key "5" [:num 5])
             (key "6" [:num 6])
             (key "-" [:minus]))
           (TR nil
             (key "1" [:num 1])
             (key "2" [:num 2])
             (key "3" [:num 3])
             (key "+" [:plus]))
           (TR nil
             (key "." [:period])
             (key "0" [:num 0])
             (key "=" [:eq] 2))])

(def main (TABLE nil
            (TBODY nil
              [(cons (TR nil (TD {:colspan 4} (DIV {:id "display"} "123")))
                     keys)])))

(render! (new-signal main) (.getElementById js/document "calculator"))
