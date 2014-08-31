(ns sortable.main
  (:require [dar.ui.frp :as frp :refer [<-]]
            [dar.ui :as ui]
            [util :as util])
  (:require-macros [dar.ui.html :refer [DIV IMG]]
                   [dar.ui.frp :as frp]))

(enable-console-print!)

(def counter (atom 0))

(defn new-uid []
  (swap! counter inc))

(defn mouse-pos [e]
  [(.-clientX e) (.-clientY e)])

(defn element-pos [el]
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
  (util/event-port* js/window :mousemove))

(def mouseup
  (util/event-port* js/window :mouseup))

(ui/install-plugin! ::key (fn [el k _]
                            (set! (.-__sortable_item_key el) k)))

(defn item-key [el]
  (.-__sortable_item_key el))

(defn target [el id]
  (when el
    (if (= id (first (item-key el)))
      (second (item-key el))
      (recur (.-parentNode el) id))))

(defn initial-state [col]
  (let [id (new-uid)
        capture (frp/new-event)
        html (<- (fn [col]
                   (ui/set-children col
                     (vec
                       (map-indexed (fn [i el]
                                      (-> el
                                        (ui/set-attributes (assoc (ui/attributes el) ::key [id i]))
                                        (ui/listen :mousedown
                                          (ui/to capture (fn [e dom-el]
                                                           [i e dom-el])))))
                         (ui/children col)))))
               col)]
    {:commands {:capture capture}
     :inputs {:col html}
     :id id
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
        :el-start-pos (element-pos el)
        :phantom (make-phantom el)
        :col col
        :items items
        :captured-idx idx
        :methods captured-state-methods
        :output {:html (fn [s]
                         (<- ui/set-children
                           (<- :col s)
                           (<- :items s)))}))))

(def captured-state-methods
  {:mouse (fn [state e]
            (let [pos (mouse-pos e)]
              (if (> 10 (distance pos (:mouse-start-pos state)))
                state
                (dragging-state state pos))))
   :mouseup (fn [state _]
              (:initial-state state))})

(defn dragging-state [{:keys [phantom] :as state} pos]
  (-> state
    (update-in [:items (:captured-idx state)] ui/add-class "is-dragging")
    (assoc :methods dragging-state-methods)
    (assoc-in [:output :phantom]
      (fn [_]
        (frp/effect
          (fn [e]
            (place-element! phantom (draggable-position state (mouse-pos e))))
          (fn []
            (remove-element! phantom))
          mousemove)))))

(defn draggable-position [state mouse-pos]
  (mapv +
    (:el-start-pos state)
    (mapv -
      mouse-pos
      (:mouse-start-pos state))))

(def dragging-state-methods
  {:mouse (fn [state e]
            (let [idx (hit-test state e)]
              (if (or (not idx) (= idx (:captured-idx state)))
                state
                (move state idx))))
   :mouseup (fn [state _]
              (:initial-state state))})

(defn hit-test [{:keys [id phantom]} e]
  (set! (.. phantom -style -display) "none")
  (let [el (js/document.elementFromPoint
             (.-clientX e)
             (.-clientY e))]
    (set! (.. phantom -style -display) nil)
    (target el id)))

(defn set-idx [el idx]
  (ui/set-attributes el
    (assoc-in (ui/attributes el) [::key 1] idx)))

(defn conj* [ret el]
  (conj ret (set-idx el (count ret))))

(defn move [{:keys [items captured-idx] :as state} target-idx]
  (let [captured (nth items captured-idx)]
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

(defn sortable [col]
  (<- :html
    (util/state-machine
      (initial-state col))))

(def app
  (frp/new-app))

(def images
  (DIV {:class "gallery"}
    [(for [i (range 1 50)]
       (IMG {:key i
             :src (str "http://lorempixel.com/120/120/nature/" (rand-int 11))
             :class "gallery-image no-drag-select"}))]))

(defn -main []
  (ui/render!
    app
    (sortable
      (frp/new-signal images))
    (js/document.getElementById "app")))
