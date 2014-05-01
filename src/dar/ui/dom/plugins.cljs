(ns dar.ui.dom.plugins
  (:require [dar.ui.dom.core :as c :refer [install-plugin! install-event!]]
            [dar.ui.dom.util :as dom]
            [dar.ui.dom.draggable :as draggable]
            [dar.ui.dom.sortable :as sortable]))

(install-plugin! :html! (fn [el html _]
                          (set! (.-innerHTML el) (str html))))

(install-plugin! :focus (fn [el focus? _]
                          (when focus?
                            (.focus el))))

(install-plugin! :value (fn [el v _]
                          (dom/set-value! el v)))

(install-plugin! :ev-change (fn [el cb _]
                              (if (nil? (.-checked el))
                                (set! (.-onchange el) (if cb
                                                        (c/listener #(do
                                                                       (dom/stop! %)
                                                                       (.-value el))
                                                                    cb)))
                                (set! (.-onclick el) (if cb
                                                       (c/listener #(do
                                                                      (.stopPropagation %)
                                                                      (.-checked el))
                                                                   cb))))))

(install-event! :ev-click :click dom/stop!)

(install-event! :ev-dblclick :dblclick dom/stop!)

(install-plugin! :draggable draggable/plugin)
