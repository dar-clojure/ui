(ns dar.ui.lib.plugins
  (:require [dar.ui.dom.core :refer [install-plugin! install-event! listener]]
            [dar.ui.dom.util :as dom]))

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
                                                        (listener #(do
                                                                     (dom/stop! %)
                                                                     (.-value el))
                                                                  cb)))
                                (set! (.-onclick el) (if cb
                                                       (listener #(do
                                                                    (.stopPropagation %)
                                                                    (.-checked el))
                                                                 cb))))))

(install-event! :ev-click :click dom/stop!)

(install-event! :ev-dblclick :dblclick dom/stop!)
