(ns dar.dom
  (:refer-clojure :exclude [type key])
  (:require [dar.dom.browser :as dom]))

(defprotocol IElement
  (type [this])
  (key [this])
  (create [this])
  (update! [this prev el])
  (remove! [this el]))

(defn update-element! [new old el]
  (if (and old (= (type new) (type old)))
    (update! new old el)
    (let [new-el (create new)]
      (dom/replace! new-el el)
      new-el)))

;
; Collection (children) rendering
;

(defn- update-non-sorted-part! [[x & xs :as new] [y & ys :as old] els append!]
  (cond (not x) (when y
                  (dorun (map remove! old els)))
        (not y) (dorun (map #(-> create append!) new))
        (identical? x y) (recur xs ys (next els) append!)
        (= (key x) (key y)) (do
                              (update-element! x y (first els))
                              (recur xs ys (next els) append!))
        :else [new old els]))

(defn- update-sorted-part! [new old els append!]
  (let [olds (into {} (map (fn [y el]
                             [(key y) [y el]])
                           old els))
        olds (reduce (fn [olds x]
                       (let [k (key x)
                             [y el] (get olds k)
                             new-el (if (identical? x y)
                                      el
                                      (update-element! x y el))]
                         (append! new-el)
                         (if y
                           (dissoc olds k)
                           olds)))
                     olds
                     new)]
    (doseq [[_ [old el]] olds]
      (remove! old el))))

(defn update-children! [new old parent]
  (let [[new old els] (update-non-sorted-part! new
                                               old
                                               (dom/children parent)
                                               #(.appendChild parent %))]

    (when (seq new)
      (let [ref (.-previousSibling (first els))
            append! #(dom/insert-after! parent % ref)
            [new old els] (update-sorted-part! (reverse new)
                                               (reverse old)
                                               (dom/children-reversed parent)
                                               append!)]
        (when (seq new)
          (update-sorted-collection! new old els append!))))))

;
; Since we don't know what is the right data structure to represent regular HTML elements
; lets guard ourselfs with protocol
;

(defprotocol IHtml
  (tag [this])
  (attributes [this])
  (set-attributes [this attr])
  (children [this]))

(defrecord Element [tag attrs children]
  IHtml
  (tag [_] tag)
  (attributes [_] attrs)
  (set-attributes [this new-attrs] (->Element tag new-attrs children))
  (children [_] children))

;
; Plugins
;

(def plugins {})

(def ^:dynamic *listener* nil)

(defn install-plugin! [name plugin]
  (def plugins (assoc plugins name plugin)))

(defn call-plugin! [name type el arg old-arg]
  (when-let [plugin (get plugins name)]
    (plugin type *listener* el arg old-arg)
    true))

(defprotocol IListener
  (push! [this handle val])
  (alive? [this handle]))

;
; Default IElement implementation for IHtml
;

(defn update-attributes! [[[k v :as attr] & new-attrs] old-attrs el]
  (if attr
    (let [old (get old-attrs k)]
      (when-not (or (identical? v old) (call-plugin! k :update el v old))
        (.setAttribute el v))
      (recur new-attrs (dissoc old-attrs k) el))
    (doseq [[k v] old-attrs]
      (when-not (call-plugin! k :update el nil v)
        (.removeAttribute el k)))))

(def html-element-impl
  {:type tag
   :key #(:key (attributes %))
   :create (fn [this]
             (let [el (.createElement js/document (tag this))]
               (doseq [child (children this)]
                 (.appendChild el (create child)))
               (doseq [[k v] (attributes this)]
                 (when-not (call-plugin! k :create el v nil)
                   (.setAttribute el k v)))
               el))
   :update! (fn [new old el]
              (let [new-children (children new)
                    old-children (children old)]
                (when-not (identical? new-children old-children)
                  (update-children! new-children old-children el)))
              (let [new-attrs (attributes new)
                    old-attrs (attributes old)]
                (when-not (identical? new-attrs old-attrs)
                  (update-attributes! new-attrs old-attrs el)))
              el)
   :remove! (fn [this el]
              (if (.hasAttribute el "data-dar-delete-animation")
                (do
                  (.setAttribute el "data-dar-deleted" true)
                  (js/setTimeout #(dom/remove! el) 1000)) ;; TODO: it should be better to use transition events
                                                          ;; We probably can even detect transition properties
                (dom/remove! el)))})

(extend Element
  IElement html-element-impl)

;
; Use string as a text node
;

(extend-type String
  IElement
  (type [_] :text-node)
  (key [_] nil)
  (create [s] (.createTextNode js/document s))
  (update! [new old node] (when-not (= new old)
                                (set! (.-textContent node) new-s)))
  (remove! [_ node] (dom/remove! node)))

;
; Hiccup style elements
;

(extend-type PersistentVector
  IHtml
  (tag [this] (nth this 0))
  (attributes [this] (nth this 1))
  (set-attributes [this attrs] (assoc this 1 attrs))
  (children [this] (nth this 2)))

(extend PersistentVector
  IElement html-element-impl)
