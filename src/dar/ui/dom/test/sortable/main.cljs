(ns dar.ui.dom.test.sortable.main
  (:require [dar.ui :as ui]
            [dar.ui.frp :as frp])
  (:require-macros [dar.ui.dom.elements :refer [UL LI]]))

(def items
  (->> (range 1 10)
       (map (fn [i]
              (LI {:key i :class "ControlGroup-item Sortable-item"}
                (str "Item " i))))))

(defn -main []
  (let [el (.createElement js/document "div")]
    (.appendChild (-> js/document .-body) el)
    (ui/render! (frp/new-signal (UL {:class "ControlGroup ControlGroup--vert Sortable "}
                                  [items]))
                el)))
