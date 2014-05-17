(ns dar.ui.lib.draggable
  (:require [dar.ui.frp :as frp]
            [dar.ui.dom.util :as dom]
            [dar.ui.lib.event :as event]))

(defn vlen [[x y]]
  (js/Math.sqrt (+ (* x x) (* y y))))

(defn mouse-position [e]
  [(.-clientX e) (.-clientY e)])

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
        (frp/switch (frp/transform [down? down? _ up?]
                      (when down?
                        (event/event js/window :mousemove mouse-position))))))

(define :mouseup
  :args [mousedown]
  :fn (fn [down?]
        (frp/switch (frp/transform [down? down?]
                      (when down?
                        (event/once js/window :mouseup)))))) ;; hack, FIXME

(define :capture
  :args [:mousedown]
  :fn identity)

(define :endcapture
  :args [:mouseup]
  :fn identity)

(define :pointer-start-pos
  :args [:capture]
  :fn to-signal)

(define :pointer-pos
  :args [:mousemove]
  :fn (fn [pos]
        (frp/foldp #(or %2 %1) [0 0] pos)))

(define :pointer-move
  :args [:pointer-start-pos :pointer-pos]
  :fn (frp/lift #(mapv - %2 %1)))

(define :captured?
  :args [:capture :endcapture]
  :fn (frp/lift (fn [c? _]
                  (boolean c?))))

(define el-start-pos [el capture]
  (frp/<- (fn [_] [(.-offsetLeft el) (.-offsetTop el)])
          capture))

(define dragging? [move endcapture stickiness]
  (frp/foldp (fn [prev? move end?]
               (cond end? false
                     prev? true
                     :else (>= (vlen move) stickiness)))
             move
             endcapture))

(define watch-capture-attr [el app captured?]
  (frp/watch! app captured? (fn [c? _]
                              (dom/set-attribute! el :data-dragging c?))))

(define watch-dragging-attr [el app dragging?]
  (frp/watch! app captured? (fn [d? _]
                              (dom/set-attribute! el :data-dragging d?))))

(defn place! [el [x y]]
  (let [s (.-style el)]
    (set! (.-position s) "absolute")
    (set! (.-left s) (str x "px"))
    (set! (.-top s) (str y "px"))))

(define watch-element-pos [el app move el-start-pos dragging?]
  (frp/watch! app
              (frp/transform [move move
                              start-pos el-start-pos
                              dragging? dragging?]
                (when dragging?
                  (mapv + start-pos move)))
              (fn [pos _]
                (when pos
                  (place! el pos)))))

(define :start
  :pre [:watch-capture-attr :watch-dragging-attr :watch-element-pos]
  :dispose true
  :args [:app]
  :fn (fn [app]
        (reify c/IDisposable
          (dispose [_] (frp/dispose! app)))))

(defn plugin [el opts old-opts]
  (when old-opts
    (let [app (dom/data el ::draggable)]
      (c/stop! app)
      (dom/set-data! el ::draggable nil)))
  (when opts
    (let [app (c/start draggable (merge opts {:el el}))]
      (dom/set-data! el ::draggable app)
      (c/eval! app :start)
      nil)))
