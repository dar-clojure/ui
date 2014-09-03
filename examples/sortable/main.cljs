(ns sortable.main
  (:require [dar.ui.frp :as frp :refer [<-]]
            [dar.ui.frp.state-machine :as sm]
            [dar.ui :as ui])
  (:require-macros [dar.ui.html :refer [DIV IMG]]
                   [dar.ui.frp :as frp]))

;
; Utils
;

(def counter (atom 0))

(defn new-uid []
  (swap! counter inc))

(defn mouse-pos [e]
  [(.-clientX e) (.-clientY e)])

(defn element-left-top [el]
  (let [r (.getBoundingClientRect el) ]
    [(+ js/window.scrollX (.-left r))
     (+ js/window.scrollY (.-top r))]))

(defn place-element! [el [x y]]
  (let [s (.-style el)]
    (set! (.-position s) "absolute")
    (set! (.-left s) (str x "px"))
    (set! (.-top s) (str y "px")))
  (when-not (.-parentNode el)
    (js/document.body.appendChild el)))

(defn remove-element! [el]
  (when-let [p (.-parentNode el)]
    (.removeChild p el)))

(defn make-phantom [el]
  (doto (.cloneNode el true)
    (.setAttribute "data-drag-phantom" true)
    (.setAttribute "data-virtual" true)))

(defn distance [[x1 y1] [x2 y2]]
  (js/Math.sqrt
    (+
      (js/Math.pow (- x2 x1) 2)
      (js/Math.pow (- y2 y1) 2))))

(def mousemove
  (ui/event-signal* js/window :mousemove))

(def mouseup
  (ui/event-signal* js/window :mouseup))

;
; Hit test mechanism
;

(ui/install-plugin! ::key (fn [el k _]
                            (set! (.-__sortable_item_key el) k)))

(defn item-key [el]
  (.-__sortable_item_key el))

(defn target [el id]
  (when el
    (if (= id (first (item-key el)))
      (second (item-key el))
      (recur (.-parentNode el) id))))

(defn hit-test [id phantom e]
  (set! (.. phantom -style -display) "none")
  (let [el (js/document.elementFromPoint
             (.-clientX e)
             (.-clientY e))]
    (set! (.. phantom -style -display) nil)
    (target el id)))

(defn annotate [virtual-el id idx]
  (ui/set-attributes virtual-el
    (assoc (ui/attributes virtual-el)
      ::key [id idx])))

(defn reset-idx [virtual-el idx]
  (ui/set-attributes virtual-el
    (assoc-in (ui/attributes virtual-el) [::key 1] idx)))

;
; State machine
;

(defn initial-state [col order]
  (let [id (new-uid)
        capture (frp/new-event)
        html (<- (fn [col]
                   (ui/set-children col
                     (vec
                       (map-indexed (fn [i el]
                                      (-> el
                                        (annotate id i)
                                        (ui/listen :mousedown
                                          (ui/to capture (fn [e dom-el]
                                                           [i e dom-el])))))
                         (ui/children col)))))
               col)]
    {:id id
     :order order
     :commands {:capture capture}
     :inputs {:col html}
     :output {:html (constantly html)}
     :methods {:capture captured-state}}))

(defn captured-state [state [idx e el]]
  (let [col (-> state :values :col)
        items (ui/children col)]
    (-> state
      (update-in [:commands] assoc
        :mouse mousemove
        :mouseup mouseup)
      (assoc
        :initial-state state
        :mouse-start-pos (mouse-pos e)
        :el-start-pos (element-left-top el)
        :phantom (make-phantom el)
        :col col
        :items items
        :captured-idx idx
        :output {:html (fn [s]
                         (<- ui/set-children
                           (<- :col s)
                           (<- :items s)))}
        :methods captured-state-methods))))

(def captured-state-methods
  {:mouse (fn [state e]
            (let [pos (mouse-pos e)]
              (when (> 10 (distance pos (:mouse-start-pos state)))
                (dragging-state state))))
   :mouseup (fn [state _]
              (:initial-state state))})

(defn dragging-state [{:keys [phantom el-start-pos mouse-start-pos] :as state}]
  (-> state
    (update-in [:items (:captured-idx state)] ui/add-class "is-dragging")
    (assoc-in [:output :phantom]
      (fn [_]
        (frp/effect
          (fn [e]
            (place-element! phantom
              (let [pos (mouse-pos e)]
                (mapv + el-start-pos (mapv - pos mouse-start-pos)))))
          (fn []
            (remove-element! phantom))
          mousemove)))
    (assoc :methods dragging-state-methods)))

(def dragging-state-methods
  {:mouse (fn [state e]
            (let [idx (hit-test (:id state) (:phantom state) e)]
              (when (and idx (not= idx (:captured-idx state)))
                (re-order-items state idx))))

   :mouseup (fn [{:keys [order items initial-state]} _]
              (assoc initial-state
                :push [[order (mapv ui/key items)]]))})

(defn re-order-items [{:keys [items captured-idx] :as state} target-idx]
  (let [captured (nth items captured-idx)
        conj* (fn [items el]
                (conj items (reset-idx el (count items))))]
    (assoc state
      :captured-idx target-idx
      :items (reduce-kv (fn [ret idx el]
                          (cond
                            (= idx captured-idx) ret
                            (= idx target-idx) (if (> target-idx captured-idx)
                                                 (-> ret (conj* el) (conj* captured))
                                                 (-> ret (conj* captured) (conj* el)))
                            :else (conj* ret el)))
               []
               items))))

(defn sortable [col order]
  (<- :html (sm/create
              (initial-state col order))))

;
; Demo
;

(def images
  (into {}
    (for [i (range 0 50)]
      [i (IMG {:key i
               :src (str "http://lorempixel.com/120/120/nature/" (rand-int 11))
               :class "gallery-image no-drag-select"})])))

(def order
  (frp/new-signal
    (sort (keys images))))

(def gallery
  (frp/bind [order order]
    (DIV {:class "gallery"}
      [(mapv #(get images %) order)])))

(defn ^:export -main []
  (ui/render! (sortable gallery order)
    (js/document.getElementById "app")))
