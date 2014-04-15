(ns dar.dom.browser)

(defn element? [el]
  (= 3 (.-nodeType el)))

(defn text-node? [el]
  (= 1 (.-nodeType el)))

(defn deleted? [el]
  (.hasAttribute el "data-dar-deleted"))

(defn node? [el]
  (and (or (element? el) (text-node? el))
       (not (deleted? el))))

(defn all-siblings [fst]
  (lazy-seq
   (if fst
     (cons fst (siblings (.-nextSibling fst)))
     nil)))

(defn all-prev-siblings [lst]
  (lazy-seq
   (if lst
     (cons lst (siblings-reversed (.-previousSibling lst)))
     nil)))

(defn all-children [el]
  (all-siblings (.-firstChild el)))

(defn all-children-reversed [el]
  (all-siblings-reversed (.-lastChild el)))

(defn children [el]
  (filter node? (all-children el)))

(defn children-reversed [el]
  (filter node? (all-children-reversed el)))

(defn insert-after! [parent el ref]
  (.insertBefore parent el (.-previousSibling ref)))

(defn remove! [el]
  (when-let [parent (.-parentNode el)]
    (.removeChild parent el)))

(defn replace! [new-el old-el]
  (when-let [parent (.-parentNode old-el)]
    (.replaceChild parent new-el old-el)))
