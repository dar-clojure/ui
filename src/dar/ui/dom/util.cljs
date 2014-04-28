(ns dar.ui.dom.util)

(defn element? [el]
  (= 1 (.-nodeType el)))

(defn text-node? [el]
  (= 3 (.-nodeType el)))

(defn deleted? [el]
  (and (element? el)
       (.hasAttribute el "data-deleted")))

(defn node? [el]
  (and (or (element? el) (text-node? el))
       (not (deleted? el))))

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

(defn remove! [el]
  (when-let [parent (.-parentNode el)]
    (.removeChild parent el)))

(defn replace! [new-el old-el]
  (when-let [parent (.-parentNode old-el)]
    (.replaceChild parent new-el old-el)))

(defn add-attribute! [el k v]
  (when-not (nil? v)
    (.setAttribute el (name k) v)))

(defn remove-attribute! [el k]
  (.removeAttribute el (name k)))

(defn set-attribute! [el k v]
  (if (nil? v)
    (remove-attribute! el k)
    (add-attribute! el k v)))

(defn data [el k] ;; TODO: use WeakMap once it will become widely adopted
  (aget el (str k)))

(defn set-data! [el k v]
  (aset el (str k) v))

(defn listen!
  ([el k f]
   (.addEventListener el (name k) f))
  ([el m]
   (doseq [[k f] m]
     (listen! el k f))))

(defn unlisten!
  ([el k f]
   (.removeEventListener el (name k) f))
  ([el m]
   (doseq [[k f] m]
     (unlisten! el k f))))

(defn cleanup! [el k]
  (when-let [f (data el k)]
    (set-data! el k nil)
    (f)))

(defn stop! [ev]
  (.preventDefault ev)
  (.stopPropagation ev)
  ev)

(defn tick [f]
  (js/setTimeout f))

(defn value [el] ;; TODO: see https://github.com/component/value
  (if (= "checkbox" (-> el .-type .toLowerCase))
    (.-checked el)
    (.-value el)))

(defn set-value! [el val]
  (if (= "checkbox" (-> el .-type .toLowerCase))
    (set! (.-checked el) (boolean val))
    (set! (.-value el) (str val))))
