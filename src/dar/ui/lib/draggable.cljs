(ns dar.ui.lib.draggable
  (:require [dar.ui.frp :as frp :include-macros true]
            [dar.ui.dom :as dom]
            [dar.ui.lib.event :as event]
            [dar.container :as co])
  (:require-macros [dar.container.macro :refer [define] :as co]))

(defn vlen [[x y]]
  (js/Math.sqrt (+ (* x x) (* y y))))

(defn mouse-position [e]
  [(.-clientX e) (.-clientY e)])

(define :handle nil)

(define :mousedown
  :args [:el :handle]
  :fn (fn [el handle]
        (event/event el :mousedown (fn [e]
                                     (when (and (= 0 (.-button e))
                                             (or (not handle)
                                               (dom/child? (.-target e) (dom/get el handle))))
                                       (dom/stop! e)
                                       (mouse-position e))))))

(define :mousemove
  :args [:mousedown :mouseup]
  :fn (fn [down? up?]
        (frp/switch (frp/bind [down? down? _ up?]
                      (when down?
                        (event/signal js/window :mousemove mouse-position))))))

(define :mouseup
  :args [:mousedown]
  :fn (fn [down?]
        (frp/as-event
          (frp/switch (frp/bind [down? down?]
                        (when down?
                          (event/once js/window :mouseup boolean))))))) ;; hack, FIXME

(define :capture
  :args [:mousedown]
  :fn identity)

(define :endcapture
  :args [:mouseup]
  :fn identity)

(define :pointer-start-pos
  :args [:capture]
  :fn frp/to-signal)

(define :pointer-pos
  :args [:mousemove]
  :fn identity)

(define :pointer-move
  :args [:pointer-pos :pointer-start-pos ]
  :fn (frp/lift (fn [p2 p1]
                  (if p2
                    (mapv - p2 p1)
                    [0 0]))))

(define :captured?
  :args [:capture :endcapture]
  :fn (frp/lift (fn [c? _]
                  (boolean c?))))

(define :el-start-pos
  :args [:el :capture]
  :fn (fn [el capture]
        (frp/<- (fn [_] [(.-offsetLeft el) (.-offsetTop el)])
          capture)))

(define :dragging?
  :args [:pointer-move :captured? :stickiness]
  :fn (fn [move captured? stickiness]
        (frp/foldp (fn [prev? [move c?]]
                     (cond
                       (not c?) false
                       prev? true
                       :else (>= (vlen move) stickiness)))
          false
          (frp/join move captured?))))

(define :stickiness 0)

(define :watch-captured-attr
  :args [:el :app :captured?]
  :fn (fn [el app captured?]
        (frp/watch! app captured? (fn [c? _]
                                    (dom/set-attribute! el :data-draggable-captured c?)))))

(define :watch-dragging-attr
  :args [:el :app :dragging?]
  :fn (fn [el app d?]
        (frp/watch! app d? (fn [d? _]
                             (dom/set-attribute! el :data-draggable-dragging d?)))))

(defn place! [el [x y]]
  (let [s (.-style el)]
    (set! (.-position s) "absolute")
    (set! (.-left s) (str x "px"))
    (set! (.-top s) (str y "px"))))

(define :watch-element-pos
  :args [:el :app :pointer-move :el-start-pos :dragging?]
  :fn (fn [el app pointer-move el-start-pos dragging?]
        (print "watch pos")
        (frp/watch! app
          (frp/bind [move pointer-move
                     start-pos el-start-pos
                     dragging? dragging?]
            (when dragging?
              (mapv + start-pos move)))
          (fn [pos _]
            (when pos
              (place! el pos))))))

(define :app
  :fn #(frp/new-app))

(define :init
  :args [:watch-captured-attr :watch-dragging-attr :watch-element-pos]
  :fn (fn [& _] ))

(def draggable (co/make))

(defn plugin [el opts old-opts]
  (when old-opts
    (let [app (dom/data el ::draggable)]
      (co/stop! app)
      (dom/set-data! el ::draggable nil)))
  (when opts
    (let [app (co/start draggable (merge opts {:el el}))]
      (dom/set-data! el ::draggable app)
      (co/eval app :init)
      (co/eval app :watch-element-pos)))) ; ???
