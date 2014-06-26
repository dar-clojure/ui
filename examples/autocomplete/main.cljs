(ns autocomplete.main
  (:require [dar.ui :as ui]
            [dar.ui.frp :as frp :include-macros true]
            [dar.ui.lib.event :as event])
  (:require-macros [dar.ui.html :refer [UL LI]]))

(defn key->command [e]
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

(defn add-focused-class [el]
  (ui/add-class el :is-focused))

(defn select
  ([el focus commands] (select el focus commands add-focused-class))
  ([el focus commands highlight]
   (let [commands (if commands
                    (frp/<- commands)
                    (frp/event))
         len (frp/<- (comp count ui/children) el)
         focus (frp/pipe
                 (frp/foldp (fn [idx len [cmd p]]
                              (case cmd
                                :next (next-idx idx len)
                                :prev (prev-idx idx len)
                                :esc nil
                                :focus p
                                nil))
                   nil
                   (frp/pullonly len)
                   commands)
                 focus)]
     (frp/bind [el el
                focus focus]
       (ui/set-children el
         (map-indexed (fn [idx item]
                        (let [item (ui/listen item :ev-mouseover (ui/to commands [:focus idx]))]
                          (if (= idx focus)
                            (highlight item)
                            item)))
           (ui/children el)))))))

;
; Demo
;

(enable-console-print!)

(def main
  (select
    (frp/signal (UL nil
                  (LI nil "first")
                  (LI nil "second")
                  (LI nil "third")
                  (LI nil "fourth")
                  (LI nil "fifth")))
    (frp/signal)
    (event/event js/document :keydown key->command)))

(defn -main []
  (let [el (.createElement js/document "div")]
    (.appendChild (-> js/document .-body) el)
    (ui/render! main el)))
