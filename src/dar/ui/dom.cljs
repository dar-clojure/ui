(ns dar.ui.dom
  (:refer-clojure :exclude [get])
  (:require [clojure.string :as string]))

(defn get
  ([ctx q] (.querySelector ctx q))
  ([q] (get js/document q)))

(defn by-id
  ([ctx id] (.getElementById ctx id))
  ([id] (by-id js/document id)))

(defn element? [el]
  (= 1 (.-nodeType el)))

(defn text-node? [el]
  (= 3 (.-nodeType el)))

(defn virtual? [el]
  (.hasAttribute el "data-virtual"))

(defn node? [el]
  (or (and (element? el) (not (virtual? el)))
      (text-node? el)))

(defn all-siblings [fst]
  (lazy-seq
   (if fst
     (cons fst (all-siblings (.-nextSibling fst)))
     nil)))

(defn all-prev-siblings [lst]
  (lazy-seq
   (if lst
     (cons lst (all-prev-siblings (.-previousSibling lst)))
     nil)))

(defn all-children [el]
  (all-siblings (.-firstChild el)))

(defn all-children-reversed [el]
  (all-prev-siblings (.-lastChild el)))

(defn children [el]
  (filter node? (all-children el)))

(defn children-reversed [el]
  (filter node? (all-children-reversed el)))

(defn insert-after! [parent el ref]
  (.insertBefore parent el (and ref (.-previousSibling ref))))

(defn add-attribute!
  ([el k]
   (add-attribute! el k true))
  ([el k v]
   (when v
     (.setAttribute el (name k) v))))

(defn remove-attribute! [el k]
  (.removeAttribute el (name k)))

(defn set-attribute! [el k v]
  (if v
    (add-attribute! el k v)
    (remove-attribute! el k)))

(defn has-attribute? [el k]
  (.hasAttribute el (name k)))

(defn child? [el parent]
  (if (identical? el parent)
    true
    (if el
      (recur (.-parentNode el) parent)
      false)))

(defn in-dom? [el]
  (child? el js/document))

(defn remove! [el]
  (when-let [parent (.-parentNode el)]
    (.removeChild parent el)))

(defn soft-remove! [el ms]
  (add-attribute! el "data-virtual")
  (add-attribute! el "data-deleted")
  (js/setTimeout #(remove! el) ms))

(defn replace! [new-el old-el]
  (when-let [parent (and old-el (.-parentNode old-el))]
    (.replaceChild parent new-el old-el)))

(defn data [el k] ;; TODO: use WeakMap once it will become widely adopted
  (aget el (str k)))

(defn set-data! [el k v]
  (aset el (str k) v))

(defn stop! [ev]
  (.preventDefault ev)
  (.stopPropagation ev)
  ev)

(defn tick [f]
  (js/setTimeout f))

(defn raf [f]
  (js/requestAnimationFrame f))

(defn listen! [el event cb]
  (.addEventListener el (name event) cb))

(defn unlisten! [el event cb]
  (.removeEventListener el (name event) cb))

(defn value [el] ;; TODO: see https://github.com/component/value
  (if (= "checkbox" (-> el .-type .toLowerCase))
    (.-checked el)
    (.-value el)))

(defn set-value! [el val]
  (if (= "checkbox" (-> el .-type .toLowerCase))
    (set! (.-checked el) (boolean val))
    (set! (.-value el) (str val))))
