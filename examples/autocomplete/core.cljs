(ns autocomplete.core
  (:require [dar.ui :as ui]
            [dar.ui.frp :as frp :include-macros true]))

(defn keypress->command [e]
  (case (.-keyCode e)
    38 [:prev]
    40 [:next]
    27 [:esc]
    nil))

(defn next-idx [idx len]
  (if (nil? idx)
    0
    (let [idx (inc idx)]
      (if (= idx len)
        0
        idx))))

(defn prev-idx [idx len]
  (if (nil? idx)
    (dec len)
    (let [idx (dec idx)]
      (if (< idx 0)
        (dec len)
        idx))))

(defn highlightable-list
  ([el] (highlightable-list el (frp/<- ui/children el)))
  ([parent list]
   (let [commands (frp/event)
         keypress (ui/to commands keypress->command)
         focused (frp/foldp (fn [idx len [cmd p]]
                              (case cmd
                                :next (next-idx idx len)
                                :prev (prev-idx idx len)
                                :esc nil
                                :focus p
                                nil))
                   nil
                   (frp/pullonly (frp/<- count list))
                   commands)]
     (frp/bind [parent parent
                list list
                focused focused]
       (ui/set-children (ui/listen parent :ev-keypress keypress)
         (map-indexed (fn [idx item]
                        (-> item
                          (ui/add-class (if (= idx focused)
                                          :is-focused))
                          (ui/listen :ev-mouseover (ui/to commands [:focus idx]))))
           list))))))
