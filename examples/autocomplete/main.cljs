(ns autocomplete.main
  (:require [dar.ui :as ui]
            [dar.ui.frp :as frp]
            [autocomplete.core :refer [highlightable-list]])
  (:require-macros [dar.ui.html :refer [UL LI]]))

(enable-console-print!)

(def main
  (highlightable-list
    (frp/signal (UL nil
                  (LI nil "first")
                  (LI nil "second")
                  (LI nil "third")
                  (LI nil "fourth")
                  (LI nil "fifth")))))

(defn -main []
  (let [el (.createElement js/document "div")]
    (.appendChild (-> js/document .-body) el)
    (ui/render! main el)))
