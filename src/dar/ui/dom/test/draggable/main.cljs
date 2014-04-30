(ns dar.ui.dom.test.draggable.main
  (:require [dar.ui :refer [render!]]
            [dar.ui.frp :as frp])
  (:require-macros [dar.ui.dom.elements :refer [DIV]]))

(def test
  (DIV nil
    (DIV {:class "Win" :draggable {:handle ".Win--title"} :id "win1"}
      (DIV {:class "Win--title"}
        "Drag me"))

    (DIV {:class "Win" :draggable {:stickiness 10} :id "win2"}
      (DIV {:class "Win--title"}
        "I am sticky"))))

(defn -main []
  (let [el (.createElement js/document "div")]
    (.appendChild (-> js/document .-body) el)
    (render! (frp/new-signal test) el)))
