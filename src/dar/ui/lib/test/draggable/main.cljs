(ns dar.ui.lib.test.draggable.main
  (:require [dar.ui :as ui]
            [dar.ui.frp :as frp]
            [dar.ui.lib.draggable])
  (:require-macros [dar.ui.html :refer [DIV]]))

(enable-console-print!)

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
    (ui/render! (frp/signal test) el)))
