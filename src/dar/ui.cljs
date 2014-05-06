(ns dar.ui
  (:require [dar.ui.dom.core :as dom-core]
            [dar.ui.dom.util :as dom-util]
            [dar.ui.dom.plugins]
            [dar.ui.frp :as frp]))

(defn render!
  ([main el] (render! (frp/new-app) main el))
  ([app main el]
   (let [el (atom el)]
     (frp/watch! app main (fn [new-html old-html]
                            (binding [dom-core/*ctx* app]
                              (swap! el #(dom-core/update-element! new-html old-html %))))))
   app))

(defn to* [proc]
  (fn [app val]
    (let [events (filter (complement nil?) (proc val))]
      (when (seq events)
        (frp/push! app events)))))

(defn to
  ([signal] (to signal nil))
  ([signal proc]
   (fn [app val]
     (let [val (cond (fn? proc) (proc val)
                     (nil? proc) val
                     :else proc)]
       (when-not (nil? val)
         (frp/push! app signal val))))))

(def classes dom-util/classes)
