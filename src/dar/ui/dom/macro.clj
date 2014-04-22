(ns dar.ui.dom.macro)

(defmacro event! [type ev-type & [proc]]
  (let [prop (symbol (str ".-on" (name ev-type)))
        proc (or proc `identity)]
    `(let [proc# ~proc]
       (dar.ui.dom/install-plugin!
        ~type
        {:on (fn [el# listener#]
               (let [fire# dar.ui.dom/*fire*]
                 (set! (~prop el#) (fn [ev#]
                                     (let [ev# (proc# ev#)]
                                       (when-not (nil? ev#)
                                         (listener# fire# ev#)))))))
         :off (fn [el# _#]
                (set! (~prop el#) nil))}))))
