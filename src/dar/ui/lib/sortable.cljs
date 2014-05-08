(ns dar.ui.dom.sortable
  (:require [dar.ui.dom.util :as dom]
            [dar.ui.dom.draggable :as draggable]))

(defn init-draggable! [el]
  (when-not (dom/data el ::draggable)
    (dom/set-data! el ::draggable true)
    (draggable/init! el)))

(def plugin {:on (fn [el _]
                   (dom/listen! el :mouseover (fn [e]
                                                (let [t (.-target e)]
                                                  (when-not (identical? el t)
                                                    (init-draggable! t)
                                                    (js/console.log (.-textContent t)))))))})
