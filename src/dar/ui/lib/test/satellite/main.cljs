(ns dar.ui.lib.test.satellite.main
  (:require [dar.ui :as ui]
            [dar.ui.frp :as frp :include-macros true]
            [dar.ui.lib.satellite])
  (:require-macros [dar.ui.html :refer [DIV A]]))

(defn example [pos]
  (let [show-s (frp/signal false)]
    (frp/bind [show? show-s]
      (A {:href "#"
          :satellite (if show?
                       [pos (DIV {:class "satellite"}
                              (name pos))])
          :ev-mouseover (ui/to show-s true)
          :ev-mouseout (ui/to show-s false)}
        (name pos)))))

(def demo
  (frp/<- (fn [examples]
            (DIV {:id "links"}
              [examples]))
    (frp/join
      (map example
        [:top
         :left
         :right
         :bottom]))))

(defn -main []
  (let [el (.createElement js/document "div")]
    (.appendChild (-> js/document .-body) el)
    (ui/render! demo el)))

(ui/install-event! :ev-mouseover :mouseover)
(ui/install-event! :ev-mouseout :mouseout)
