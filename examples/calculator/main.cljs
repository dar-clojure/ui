(ns calculator.main
  (:refer-clojure :exclude [key keys])
  (:require [dar.ui :refer [render! to]]
            [dar.ui.frp :refer [new-event foldp]])
  (:require-macros [dar.ui.macro :refer [transform TABLE TBODY TD TR DIV]]))

(def initial-state {:digits nil
                    :decimal-point nil
                    :amount nil
                    :accumulator 0
                    :operator nil
                    :memory 0})

(defn amount
  "Number on the screen"
  [{:keys [digits decimal-point accumulator amount]}]
  (if digits
    (/ digits (or decimal-point 1))
    (or amount accumulator)))

(defn on-op [f]
  (fn [{:keys [operator accumulator] :as st}]
    (assoc st
      :accumulator (if operator
                     (operator accumulator (amount st))
                     (amount st))
      :digits nil
      :decimal-point nil
      :amount nil
      :operator f)))

(def on-command
  {:num (fn [{:keys [digits decimal-point] :as st} num]
          (if digits
            (assoc st
              :digits (+ (* digits 10) num)
              :decimal-point (if decimal-point
                               (* 10 decimal-point)))
            (assoc st :digits num)))
   :period (fn [st]
             (if (:decimal-point st)
               st
               (assoc st :decimal-point 1)))
   :plus (on-op +)
   :minus (on-op -)
   :div (on-op /)
   :mult (on-op *)
   :eq (on-op (fn [_ amount] amount))
   :ac (fn [st]
         (assoc initial-state :memory (:memory st)))
   :ms (fn [st]
         (assoc st :memory (amount st)))
   :mr (fn [st]
         (assoc st :amount (:memory st)))})

(defn state-sf [commands-signal]
  (foldp (fn [st [cmd & args]]
           (if cmd
             (apply (on-command cmd) st args)
             st))
         initial-state
         commands-signal))

(defn trim-right-char [s]
  (subs s 0 (dec (count s))))

(defn format-number [num]
  (loop [s (.toFixed num 12)]
    (case (last s)
      \0 (recur (trim-right-char s))
      \. (trim-right-char s)
      s)))

(defn print-amount [st]
  (let [num (amount st)]
    (if (:decimal-point st)
      (.toFixed num (-> (:decimal-point st) str count dec))
      (format-number num))))

(defn display [st]
  (TR nil
    (TD {:colspan 4}
      (DIV {:id "display"} (print-amount st)))))

(defn key
  ([commands-signal label command] (key commands-signal label command 1))
  ([commands-signal label command span]
   (TD {:colspan span}
     (DIV {:id (-> command first name) :ev-click (to commands-signal command)}
       label))))

(defn keys [commands-signal]
  (let [key (partial key commands-signal)]
    [(TR nil
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
       (key "=" [:eq] 2))]))

(defn new-calc []
  (let [commands (new-event)
        keys (keys commands)
        state (state-sf commands)]
    (transform [s state]
      (TABLE nil
        (TBODY nil
          [(cons (display s) keys)])))))

(render! (new-calc) (.getElementById js/document "calc"))
