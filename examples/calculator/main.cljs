(ns calculator.main
  (:refer-clojure :exclude [key keys])
  (:require [dar.ui :refer [render!]]
            [dar.ui.frp :refer [new-event foldp]])
  (:require-macros [dar.ui.macro :refer [transform TABLE TBODY TD TR DIV]]))

(def initial-state {:digits nil
                    :decimal-point nil
                    :accumulator 0
                    :operation nil
                    :memory 0})

(defn amount [{:keys [digits decimal-point]}]
  (/ (or digits 0) (or decimal-point 1)))

(defn on-op [f]
  (fn [{:keys [operation accumulator] :as st}]
    (assoc st
      :accumulator (if operation
                     (operation accumulator (amount st))
                     (amount st))
      :digits nil
      :decimal-point nil
      :operation f)))

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
   :eq (on-op identity)
   :ac (fn [st]
         (assoc initial-state :memory (:memory st)))})

(defn trim-right-char [s]
  (subs s 0 (dec (count s))))

(defn format-number [num]
  (loop [s (.toFixed accumulator 12)]
    (case (last s)
      \0 (recur (trim-right-char s))
      \. (trim-right-char s)
      s)))

(defn display-string [st]
  (format-number (if (:digits st)
                   (amount st)
                   (:accumulator st))))

(defn display [st]
  (TR nil
    (TD {:colspan 4}
      (DIV {:id "display"} (display-string st)))))

(defn key
  ([commands-signal command label] (key commands-signal command label 1))
  ([commands-signal command label span]
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
        state (foldp (fn [state [cmd & args]]
                       (apply (on-command cmd) state args))
                     commands initial-state)]
    (transform [st state]
      (TABLE nil
        (TBODY nil
          [(cons (display st) keys)])))))

(render! (new-calc) (.getElementById js/document "calculator"))
