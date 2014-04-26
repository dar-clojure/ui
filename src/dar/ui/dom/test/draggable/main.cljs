(ns dar.ui.dom.test.draggable.main
  (:require [dar.ui :refer [render!]]
            [dar.ui.frp :as frp])
  (:require-macros [dar.ui.dom.elements :refer [DIV]]))

(def test
  (DIV {:class "Draggable" :draggable true}
    (DIV {:class "Draggable--handle"})))

(defn -main []
  (let [el (.createElement js/document "div")]
    (.appendChild (-> js/document .-body) el)
    (render! (frp/new-signal test) el)))
