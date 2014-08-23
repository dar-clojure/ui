(ns dar.ui
  (:refer-clojure :exclude [type key remove])
  (:require [dar.ui.frp :as frp]
            [dar.ui.util :as util]
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
      (nil? new) (do (remove old el) nil)
      (nil? old) (doto (create new) (util/replace el))
      (= (type new) (type old)) (update new old el)
      :else (doto (create new) (util/replace el)))))

;
; Collection (children) rendering
;

(defn- create-children [append children]
  (doseq [x children]
    (append
      (create x))))

(defn- remove-children [next-element el children]
  (when-let [x (first children)]
    (let [next-el (next-element el)]
      (remove x el)
      (recur next-element next-el (next children)))))

(defn- update-non-sorted-children [next-element append el new-children old-children]
  (loop [new new-children
         old old-children
         el el]
    (if-let [n (first new)]
      (if-let [o (first old)]
        (cond
          (identical? n o) (recur
                             (next new)
                             (next old)
                             (next-element el))
          (= (key n) (key o)) (recur
                                (next new)
                                (next old)
                                (next-element
                                  (update! el n o)))
          :else [new old el])
        (create-children append new))
      (remove-children next-element el old))))

(defn update-children! [parent new-children old-children]
  (let [[rest-new rest-old ref] (update-non-sorted-children
                                  util/nextSibling
                                  #(.appendChild parent %)
                                  (util/firstChild parent)
                                  new-children
                                  old-children)]
    (when (seq rest-new)
      (let [[left-new left-old] (update-non-sorted-children
                                  util/prevSibling
                                  #(.insertBefore parent % ref)
                                  (util/lastChild parent)
                                  (reverse rest-new)
                                  (reverse rest-old))]
        (when (seq left-new)
          (throw (js/Error. "This is not yet supported")))))))

;
; Plugins
;

(def plugins (js-obj))

(defn install-plugin!
  [name f]
  (aset plugins (str name) f))

(defn update-plugin! [el name new old]
  (when-let [plugin (aget plugins (str name))]
    (plugin el new old)
    true))

(defn- diff [f m1 m2]
  (if-let [[k v1] (first m1)]
    (let [v2 (get m2 k)]
      (when-not (identical? v1 v2)
        (f k v1 v2))
      (recur f (next m1) (dissoc m2 k)))
    (doseq [[k v2] m2]
      (f k nil v2))))

(defn update-attributes! [el new old]
  (diff (fn [k new old]
          (or
            (update-plugin! el k new old)
            (if (nil? new)
              (.removeAttribute el (name k))
              (.setAttribute el (name k) (str new)))))
    new
    old))

(defn set-attributes! [el m]
  (doseq [[k v] m]
    (or
      (update-plugin! el k v nil)
      (when-not (nil? v)
        (.setAttribute el (name k) (str v))))))

;
; HTML Element
;

(defprotocol IHtml
  (tag [this])
  (attributes [this])
  (set-attributes [this attr])
  (children [this])
  (set-children [this children]))

(deftype HtmlElement [tag attrs ch]
  IHtml
  (tag [this] tag)
  (attributes [this] attrs)
  (set-attributes [this m] (HtmlElement. tag m child))
  (children [this] ch)
  (set-children [this col] (HtmlElement. tag attrs col))

  IElement
  (type [this] tag)
  (key [this] (:key attrs))

  (create [this] (let [el (.createElement js/document (name tag))]
                   (set-attributes! el attrs)
                   (doseq [child (children this)]
                     (.appendChild el (create child)))
                   el))

  (update [new old el] (do
                         (let [new-attrs (attributes new)
                               old-attrs (attributes old)]
                           (when-not (identical? new-attrs old-attrs)
                             (update-attributes! el new-attrs old-attrs)))
                         (let [new-children (children new)
                               old-children (children old)]
                           (when-not (identical? new-children old-children)
                             (update-children! el new-children old-children)))
                         el))

  (remove [this el] (if-let [ms (:soft-remove (attributes this))]
                      (util/softRemove el ms)
                      (util/remove el))))


;
; Use string as a text node
;

(extend-protocol IElement
  string
  (type [_] :text-node)
  (key [_] nil)
  (create [s] (.createTextNode js/document s))
  (update [new old node] (when-not (identical? new old)
                           (set! (.-textContent node) new)))
  (remove [_ node] (util/remove node))

  nil
  (type [_] :text-node)
  (key [_] nil)
  (create [_] (.createTextNode js/document ""))
  (update [_ _ _])
  (remove [_ node] (util/remove node)))

;
; events
;

(def ^:dynamic *event-handler* nil)

(defn create-event-handler [app]
  (fn [e]
    (this-as el
      (frp/push! app
        (filter (complement nil?)
          (apply concat
            (map #(% e)
              (util/listeners el (.-type e)))))))))

(defn to* [proc]
  (fn [e]
    (proc e)))

(defn to
  ([signal] (to signal nil))
  ([signal proc]
   (fn [e]
     (let [val (cond (fn? proc) (proc e)
                     (nil? proc) e
                     :else proc)]
       (when-not (nil? val)
         [[signal val]])))))

(defn install-event! [k type f]
  (let [events-key (str k)]
    (install-plugin! k (fn [el listener old-listener]
                         (.updateListener (util/events el *event-handler*)
                           (name type)
                           events-key
                           (fn [e]
                             (when-let [v (f e)]
                               (listener v)))
                           old-listener)))))

(defn- stop! [e]
  (doto e
    (.preventDefault)
    (.stopPropagation)))

;
; App rendering
;

(defn render!
  ([main el] (render! (frp/new-app) main el))
  ([app main el]
   (let [el (atom el)
         event-handler (create-event-handler app)]
     (frp/watch! app main (fn [new-html old-html]
                            (binding [*event-handler* event-handler]
                              (swap! el #(update! % new-html old-html)))))
     app)))

;
; Built-in plugins
;

(install-plugin! :key nil)

(install-plugin! :soft-remove nil)

(install-plugin! :events (fn [el m old-m]
                           (let [events (util/events el *event-handler*)]
                             (diff (fn [k l old-l]
                                     (.updateListener events k l old-l))
                               m
                               old-m))))

(install-plugin! :html! (fn [el html _]
                          (set! (.-innerHTML el) (str html))))

(install-plugin! :value (fn [el v _]
                          (if (nil? (.-checked el))
                            (set! (.-value el) v)
                            (set! (.-checked el) (boolean v)))))

(install-plugin! :changes (fn [el listener old-listener]
                            (let [checkbox? (not (nil? (.-checked el)))
                                  event-type (if checkbox?
                                               "click"
                                               "change")]
                              (.updateListener (util/events el *event-handler*)
                                event-type
                                "changes"
                                (fn [e]
                                  (if checkbox?
                                    (.stopPropagation e)
                                    (stop! e))
                                  (listener
                                    (if checkbox?
                                      (.. e -target -checked)
                                      (.. e -target -value))))
                                old-listener))))

(install-event! :clicks :click stop!)

(install-event! :dblclicks :dblclick stop!)


;
; Virtual DOM utils
;

(defn update-attributes [el f]
  (set-attributes el (f (attributes el))))

(defn update-attribute [el k f]
  (update-attributes el (fn [attrs]
                          (let [v (f (get attrs k))]
                            (if (nil? v)
                              (dissoc attrs k)
                              (assoc attrs k v))))))

(defn classes [m]
  (loop [ret nil
         m m]
    (if-let [[k i?] (first m)]
      (if i?
        (if (nil? ret)
          (recur (name k) (next m))
          (recur (str ret " " (name k)) (next m)))
        (recur ret (next m)))
      ret)))

(defn add-class
  ([el class]
   (update-attribute el :class (fn [c]
                                 (if (seq c)
                                   (str c " " (name class))
                                   (name class))))))
