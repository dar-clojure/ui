(ns sortable.main
  (:require [dar.ui.frp :as frp :include-macros true]
            [dar.ui :as ui]
            [util :as util])
  (:require-macros [dar.ui.html :refer [DIV IMG]]))

(enable-console-print!)

(def counter (atom 0))

(defn new-uid []
  (swap! counter inc))

(defn mouse-pos [e]
  [(+ js/window.scrollX (.-screenX e))
   (+ js/window.scrollY (.-screenY e))])

(defn element-pos [el]
  (let [r (.getBoundingClientRect el) ]
    [(+ js/window.scrollX (.-left r))
     (+ js/window.scrollY (.-top r))]))

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
        html (frp/<- (fn [col]
                       (ui/set-children col
                         (vec
                           (map-indexed (fn [i el]
                                  (-> el
                                    (ui/set-attributes (assoc (ui/attributes el) ::key [id i]))
                                    (ui/listen :mousedown
                                      (ui/to capture (fn [e dom-el]
                                                       [i
                                                        (mouse-pos e)
                                                        (element-pos dom-el)
                                                        (.cloneNode dom-el true)])))))
                             (ui/children col)))))
               col)]
    {:commands {:capture capture}
     :inputs {:col html}
     :id id
     :output {:html (constantly html)}
     :methods {:capture captured-state}}))

(defn captured-state [state [idx mouse-pos el-pos phantom]]
  (let [col (-> state :values :col)
        items (ui/children col)]
    (-> state
      (update-in [:commands] assoc
        :mouse mousemove
        :mouseup mouseup)
      (assoc
        :initial-state state
        :mouse-start-pos mouse-pos
        :el-start-pos el-pos
        :phantom phantom
        :col col
        :items items
        :captured-idx idx
        :methods captured-state-methods
        :output {:html (fn [s]
                         (frp/<- ui/set-children
                           (frp/<- :col s)
                           (frp/<- :items s)))}))))

(def captured-state-methods
  {:mouse (fn [state e]
            (let [pos (mouse-pos e)]
              (if (> 10 (distance pos (:mouse-start-pos state)))
                state
                (dragging-state state pos))))
   :mouseup (fn [state _]
              (:initial-state state))})

(defn position-draggable [state mouse-pos]
  (mapv +
    (:el-start-pos state)
    (mapv -
      mouse-pos
      (:mouse-start-pos state))))

(defn dragging-state [state pos]
  (-> state
    (update-in [:items (:captured-idx state)] ui/add-class "is-dragging")
    (assoc :methods dragging-state-methods)))

(def dragging-state-methods
  {:mouse (fn [state e]
            (if-let [idx (hit-test state e)]
              (if (= idx (:captured-idx state))
                state
                (move state idx))
              state))
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
  (frp/<- :html
    (util/state-machine
      (initial-state col))))

(def app
  (frp/new-app))

(def images
  (DIV {:class "gallery"}
    [(for [i (range 1 11)]
       (IMG {:key i
             :src (str "http://lorempixel.com/120/120/nature/" i)
             :class "gallery-image no-drag-select"}))]))

(defn -main []
  (ui/render!
    app
    (sortable
      (frp/new-signal images))
    (js/document.getElementById "app")))
