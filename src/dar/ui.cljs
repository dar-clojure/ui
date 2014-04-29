(ns dar.ui
  (:require [dar.ui.dom.core :as dom-core]
            [dar.ui.dom.util :as dom-util]
            [dar.ui.dom.plugins]
            [dar.ui.frp :as frp]
            [dar.ui.frp.app :as a]))

(defn render!
  ([main el] (render! (a/new-app) main el))
  ([app main el]
   (let [html (a/pull! app main)
         push! (partial a/push! app)
         el (binding [dom-core/*raise* push!]
              (dom-core/update-element! html nil el))]
     (a/watch! app (fn [new old]
                        (let [new-html (frp/probe new main)
                              old-html (frp/probe old main)]
                          (binding [dom-core/*raise* push!]
                            (dom-core/update-element! new-html old-html el)))))
     app)))

(defn to* [proc]
  (fn [push! val]
    (let [events (filter (complement nil?) (proc val))]
      (when (seq events)
        (push! events)))))

(defn to
  ([signal] (to signal nil))
  ([signal proc]
   (fn [push! val]
     (let [val (cond (fn? proc) (proc val)
                     (nil? proc) val
                     :else proc)]
       (when-not (nil? val)
         (push! signal val))))))

(def new-app a/new-app)

(def classes dom-util/classes)
