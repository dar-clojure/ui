(ns dar.ui.dom
  (:refer-clojure :exclude [type key])
  (:require [dar.ui.dom.browser :as dom]
            [clojure.string :as string])
  (:require-macros [dar.ui.dom.macro :refer [event!]]))

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

(def plugins (js-obj))

(defrecord Plugin [on off])

(defn install-plugin!
  [name plugin]
  (aset plugins (str name) (map->Plugin plugin)))

(defn on-plugin! [name el arg]
  (when-let [{on! :on} (aget plugins (str name))]
    (when on!
      (on! el arg))
    true))

(defn off-plugin! [name el arg]
  (when-let [{off! :off} (aget plugins (str name))]
    (when off!
      (off! el arg))
    true))

(defn update-plugin! [name el new-arg old-arg]
  (when-let [{on! :on} (aget plugins (str name))]
    (when on!
      (on! el new-arg))
    true))

;
; Default IElement implementation for IHtml
;

(defn update-attributes! [new-attrs old-attrs el]
  (loop [kvs (seq new-attrs)
         old-attrs old-attrs]
    (if-let [[k v] (first kvs)]
      (let [old (get old-attrs k)]
        (when-not (or (identical? v old) (update-plugin! k el v old))
          (.setAttribute el (name k) v))
        (recur (next kvs) (dissoc old-attrs k)))
      (doseq [[k v] old-attrs]
        (when-not (off-plugin! k el v)
          (.removeAttribute el k))))))

(extend-type Element
  IElement
  (type [this] (tag this))
  (key [this] (:key (attributes this)))
  (create [this] (let [el (.createElement js/document (name (tag this)))]
                   (doseq [child (children this)]
                     (.appendChild el (create child)))
                   (doseq [[k v] (attributes this)]
                     (when-not (on-plugin! k el v)
                       (.setAttribute el (name k) v)))
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
  (remove! [this el] (if (:soft-delete (attributes this))
                       (do
                         (.setAttribute el "data-deleted" true)
                         (js/setTimeout #(dom/remove! el) 3000)) ;; TODO: it is probably better to use transition events
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

(def ^:dynamic *fire* nil)

(defn to* [proc]
  (fn [fire! val]
    (let [events (filter (complement nil?) (proc val))]
      (when (seq events)
        (fire! events)))))

(defn to
  ([signal] (to signal nil))
  ([signal proc]
   (fn [fire! val]
     (let [val (cond (fn? proc) (proc val)
                     (nil? proc) val
                     :else proc)]
       (when-not (nil? val)
         (fire! signal val))))))

;
; built-in plugins
;

(install-plugin! :key nil)
(install-plugin! :soft-delete nil)

(install-plugin! :html! {:on (fn [el html]
                               (set! (.-innerHTML el) html))})

(install-plugin! :focus {:on (fn [el focus?]
                               (when focus?
                                 (.focus el)))})

(install-plugin! :value {:on dom/set-value!})

(install-plugin! :ev-change {:on (fn [el listener]
                                   (let [fire! *fire*]
                                     (if (nil? (.-checked el))
                                       (set! (.-onchange el) #(listener fire! (do
                                                                                (dom/stop! %)
                                                                                (.-value el))))
                                       (set! (.-onclick el) #(listener fire! (do
                                                                               (.stopPropagation %)
                                                                               (.-checked el)))))))
                             :off (fn [el _]
                                    (if (nil? (.-checked el))
                                      (set! (.-onchange el) nil)
                                      (set! (.-onclick el) nil)))})

(event! :ev-click :click dom/stop!)

(event! :ev-dblclick :dblclick dom/stop!)

;
; Helpers
;

(defn classes
  ([m]
   (string/join " " (map (fn [[class on?]]
                           (if on? (name class)))
                         m)))
  ([class on?] (if on? (name class)))
  ([class on? & rest] (classes (cons [class on?] (partition 2 rest)))))
