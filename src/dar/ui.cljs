(ns dar.ui
  (:refer-clojure :exclude [type key remove])
  (:require [dar.ui.frp :as frp]
            [dar.ui.dom :as dom]
            [clojure.string :as string]))

(defprotocol IElement
  (type [this])
  (key [this])
  (create [this])
  (update [this prev el])
  (remove [this el]))

(defn update! [el new old]
  (if (identical? new old)
    el
    (cond
      (not new) (do (remove old el) nil)
      (not old) (doto (create new) (dom/replace! el))
      (= (type new) (type old)) (update new old el)
      :else (doto (create new) (dom/replace! el)))))

;
; Collection (children) rendering
;

(defn- update-non-sorted-part! [[x & xs :as new] [y & ys :as old] els append!]
  (cond (not x) (when y
                  (dorun (map remove old els)))
        (not y) (dorun (map #(-> % create append!) new))
        (identical? x y) (recur xs ys (next els) append!)
        (= (key x) (key y)) (do
                              (update! (first els) x y)
                              (recur xs ys (next els) append!))
        :else [new old els]))

(defn- update-sorted-part! [new old els append!]
  (let [olds (into {} (map (fn [y el]
                             [(key y) [y el]])
                           old els))
        olds (reduce (fn [olds x]
                       (let [k (key x)
                             [y el] (get olds k)
                             new-el (update! el x y)]
                         (append! new-el)
                         (if y
                           (dissoc olds k)
                           olds)))
                     olds
                     new)]
    (doseq [[_ [old el]] olds]
      (remove old el))))

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

(defn diff [f m1 m2]
  (loop [kvs (seq m1)
         m2 m2]
    (if-let [[k v1] (first kvs)]
      (let [v2 (get m2 k)]
        (when-not (identical? v1 v2)
          (f k v1 v2))
        (recur (next kvs) (dissoc m2 k)))
      (doseq [[k v2] m2]
        (f k nil v2)))))

(defn update-attributes! [new old el]
  (diff (fn [k new old]
          (if new
            (or
              (update-plugin! k el new old)
              (dom/set-attribute! el k new))
            (or
              (remove-plugin! k el old)
              (dom/remove-attribute! el k))))
    new
    old))

(extend-type Element
  IElement
  (type [this] (tag this))
  (key [this] (:key (attributes this)))
  (create [this] (let [el (.createElement js/document (name (tag this)))]
                   (dom/set-data! el :proto this)
                   (doseq [child (children this)]
                     (.appendChild el (create child)))
                   (doseq [[k v] (attributes this)]
                     (when-not (add-plugin! k el v)
                       (dom/add-attribute! el k v)))
                   el))
  (update [new old el] (do
                         (dom/set-data! el :proto this)
                         (let [new-children (children new)
                               old-children (children old)]
                           (when-not (identical? new-children old-children)
                             (update-children! new-children old-children el)))
                         (let [new-attrs (attributes new)
                               old-attrs (attributes old)]
                           (when-not (identical? new-attrs old-attrs)
                             (update-attributes! new-attrs old-attrs el)))
                         el))
  (remove [this el] (if-let [ms (:soft-remove (attributes this))]
                      (dom/soft-remove! el (if (number? ms)
                                             ms 300))
                      (dom/remove! el))))

;
; Use string as a text node
;

(extend-type string
  IElement
  (type [_] :text-node)
  (key [_] nil)
  (create [s] (.createTextNode js/document s))
  (update [new old node] (when-not (identical? new old)
                           (set! (.-textContent node) new)))
  (remove [_ node] (dom/remove! node)))

;
; events
;

(def ^:dynamic *app* nil)

(defn dom-listener [f]
  (partial f *app*))

(defn install-event!
  ([k event] (install-event! k event identity))
  ([k event proc]
   (let [setter (str "on" (name event))]
     (install-plugin! k (fn [el f _]
                          (aset el setter (if f
                                            (let [cb (dom-listener f)]
                                              (fn [e]
                                                (let [v (proc e)]
                                                  (when-not (nil? v)
                                                    (cb v))))))))))))

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

;
; App rendering
;

(defn render!
  ([main el] (render! (frp/new-app) main el))
  ([app main el]
   (let [el (atom el)]
     (frp/watch! app main (fn [new-html old-html]
                            (binding [*app* app]
                              (swap! el #(update! % new-html old-html))))))
   app))

;
; Built-in plugins
;

(install-plugin! :key nil)

(install-plugin! :soft-remove nil)

(install-plugin! :html! (fn [el html _]
                          (set! (.-innerHTML el) (str html))))

(install-plugin! :value (fn [el v _]
                          (dom/set-value! el v)))

(install-plugin! :ev-change (fn [el cb _] ;; TODO: this is not serious
                              (if (nil? (.-checked el))
                                (set! (.-onchange el) (if cb
                                                        (dom-listener (fn [app e]
                                                                        (dom/stop! e)
                                                                        (cb app (.-value e))))))
                                (set! (.-onclick el) (if cb
                                                       (dom-listener (fn [app e]
                                                                       (.stopPropagation e)
                                                                       (cb app (.-checked el)))))))))

(install-event! :ev-click :click dom/stop!)

(install-event! :ev-dblclick :dblclick dom/stop!)


;
; Utils
;

(defn classes
  ([m]
   (let [ret (string/join " " (->> m (filter second) (map #(-> % first name))))]
     (if (seq ret)
       ret)))
  ([class on?] (if on? (name class)))
  ([class on? & rest] (classes (cons [class on?] (partition 2 rest)))))
