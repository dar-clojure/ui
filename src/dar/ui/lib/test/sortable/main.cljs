(ns dar.ui.dom.test.sortable.main
  (:require [dar.ui :as ui]
            [dar.ui.frp :as frp])
  (:require-macros [dar.ui.dom.elements :refer [UL LI]]
                   [dar.ui.frp :refer [transform]]))


(def reoder (frp/new-event))

(def order (frp/foldp (fn [o [ref idx]]
                        (reduce (fn [o i]
                                  (cond (= i idx) o
                                        (= i ref) (-> o (conj ref) (conj idx))
                                        :else (conj o i)))
                                []
                                o))
                      (range 10)
                      reoder))

(def items  (->> (range 10)
                 (mapv (fn [i]
                         (LI {:key i :class "ControlGroup-item Sortable-item"}
                           (str "Item " i))))))

(def main (transform [order order]
            (UL {:class "ControlGroup ControlGroup--vert Sortable"
                 :sortable true}
              [(map #(nth items %) order)])))


(defn -main []
  (let [el (.createElement js/document "div")]
    (.appendChild (-> js/document .-body) el)
    (ui/render! main el)))
