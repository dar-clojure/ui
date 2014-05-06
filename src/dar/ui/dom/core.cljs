(ns dar.ui.dom.core
  (:refer-clojure :exclude [type key])
  (:require [dar.ui.dom.util :as dom]))

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
        (not y) (dorun (map #(-> % create append!) new))
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
            [new old els] (update-non-sorted-part! (reverse new)
                                                   (reverse old)
                                                   (dom/children-reversed parent)
                                                   append!)]
        (when (seq new)
          (let [ref (.-nextSibling (first els))]
            (update-sorted-part! (reverse new) old els #(.insertBefore parent % ref))))))))

;
; HTML Elements
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

(def plugins (js-obj))

(defn install-plugin!
  [name f]
  (aset plugins (str name) f))

(defn update-plugin! [name el new old]
  (when-let [plugin! (aget plugins (str name))]
    (plugin! el new old)
    true))

(defn add-plugin! [name el arg]
  (update-plugin! name el arg nil))

(defn remove-plugin! [name el arg]
  (update-plugin! name el nil arg))

;
; Default IElement implementation for IHtml
;

(defn update-attributes! [new-attrs old-attrs el]
  (loop [kvs (seq new-attrs)
         old-attrs old-attrs]
    (if-let [[k v] (first kvs)]
      (let [old (get old-attrs k)]
        (when-not (or (identical? v old) (update-plugin! k el v old))
          (dom/set-attribute! el k v))
        (recur (next kvs) (dissoc old-attrs k)))
      (doseq [[k v] old-attrs]
        (when-not (remove-plugin! k el v)
          (dom/remove-attribute! el k))))))

(extend-type Element
  IElement
  (type [this] (tag this))
  (key [this] (:key (attributes this)))
  (create [this] (let [el (.createElement js/document (name (tag this)))]
                   (doseq [child (children this)]
                     (.appendChild el (create child)))
                   (doseq [[k v] (attributes this)]
                     (when-not (add-plugin! k el v)
                       (dom/add-attribute! el k v)))
                   el))
  (update! [new old el] (do
                          (let [new-children (children new)
                                old-children (children old)]
                            (when-not (identical? new-children old-children)
                              (update-children! new-children old-children el)))
                          (let [new-attrs (attributes new)
                                old-attrs (attributes old)]
                            (when-not (identical? new-attrs old-attrs)
                              (update-attributes! new-attrs old-attrs el)))
                          el))
  (remove! [this el] (if (:soft-remove (attributes this))
                       (dom/soft-remove! el 3000)
                       (dom/remove! el))))

;
; Use string as a text node
;

(extend-type string
  IElement
  (type [_] :text-node)
  (key [_] nil)
  (create [s] (.createTextNode js/document s))
  (update! [new old node] (when-not (= new old)
                            (set! (.-textContent node) new)))
  (remove! [_ node] (dom/remove! node)))

;
; events
;

(def ^:dynamic *ctx* nil)

(defn listener
  ([cb] (partial cb *ctx*))
  ([proc cb]
   (let [cb (listener cb)]
     (fn [e]
       (let [e (proc e)]
         (when-not (nil? e)
           (cb e)))))))

(defn install-event!
  ([k event] (install-event! k event identity))
  ([k event proc]
   (let [setter (str "on" (name event))]
     (install-plugin! k (fn [el cb]
                          (aset el setter (if cb
                                            (listener proc cb))))))))

;
; built-in plugins
;

(install-plugin! :key nil)
(install-plugin! :soft-remove nil)
