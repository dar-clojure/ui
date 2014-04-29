(ns dar.ui.dom.plugins
  (:require [dar.ui.dom.core :as c :refer [install-plugin! install-event!]]
            [dar.ui.dom.util :as dom]
            [dar.ui.dom.draggable :as draggable]
            [dar.ui.dom.sortable :as sortable]))

(install-plugin! :html! {:on (fn [el html]
                               (set! (.-innerHTML el) html))})

(install-plugin! :focus {:on (fn [el focus?]
                               (when focus?
                                 (.focus el)))})

(install-plugin! :value {:on dom/set-value!})

(install-plugin! :ev-change {:on (fn [el cb]
                                   (if (nil? (.-checked el))
                                     (set! (.-onchange el) (c/listener #(do
                                                                          (dom/stop! %)
                                                                          (.-value el))
                                                                       cb))
                                     (set! (.-onclick el) (c/listener #(do
                                                                         (.stopPropagation %)
                                                                         (.-checked el))
                                                                      cb))))
                             :off (fn [el _]
                                    (if (nil? (.-checked el))
                                      (set! (.-onchange el) nil)
                                      (set! (.-onclick el) nil)))})

(install-event! :ev-click :click dom/stop!)

(install-event! :ev-dblclick :dblclick dom/stop!)

(install-plugin! :draggable draggable/plugin)

(install-plugin! :sortable sortable/plugin)
