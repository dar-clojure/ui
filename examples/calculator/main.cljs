(ns calculator.main
  (:refer-clojure :exclude [key keys])
  (:require [dar.ui :as ui :refer [to]]
            [dar.ui.frp :as frp :include-macros true])
  (:require-macros [dar.ui.html :refer [TABLE TBODY TD TR DIV]]))

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
  (fn [{:keys [operator accumulator] :as state}]
    (assoc state
      :accumulator (if operator
                     (operator accumulator (amount state))
                     (amount state))
      :digits nil
      :decimal-point nil
      :amount nil
      :operator f)))

(def on-command
  {:num (fn [{:keys [digits decimal-point] :as state} num]
          (if digits
            (assoc state
              :digits (+ (* digits 10) num)
              :decimal-point (if decimal-point
                               (* 10 decimal-point)))
            (assoc state :digits num)))
   :period (fn [state]
             (if (:decimal-point state)
               state
               (assoc state :decimal-point 1)))
   :plus (on-op +)
   :minus (on-op -)
   :div (on-op /)
   :mult (on-op *)
   :eq (on-op (fn [_ amount] amount))
   :ac (fn [state]
         (assoc initial-state :memory (:memory state)))
   :ms (fn [state]
         (assoc state :memory (amount state)))
   :mr (fn [state]
         (assoc state :amount (:memory state)))})

(defn trim-right-char [s]
  (subs s 0 (dec (count s))))

(defn format-number [num]
  (loop [s (.toFixed num 12)]
    (case (last s)
      \0 (recur (trim-right-char s))
      \. (trim-right-char s)
      s)))

(defn print-amount [state]
  (let [num (amount state)]
    (if (:decimal-point state)
      (.toFixed num (-> (:decimal-point state) str count dec))
      (format-number num))))

(defn display [state]
  (TR nil
    (TD {:colspan 4}
      (DIV {:id "display"} (print-amount state)))))

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
  (let [commands (frp/event)
        keys (keys commands)
        state (frp/automaton initial-state on-command commands)]
    (frp/transform [s state]
      (TABLE nil
        (TBODY nil
          [(cons (display s) keys)])))))

(defn -main []
  (ui/render! (new-calc) (.getElementById js/document "calc")))
