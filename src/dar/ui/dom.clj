(ns dar.ui.dom)

(defmacro install-event! [type ev-type & [proc]]
  (let [prop (symbol (str ".-on" (name ev-type)))
        proc (or proc `identity)]
    `(let [proc# ~proc]
       (install-plugin!
        ~type
        {:on (fn [el# listener#]
               (let [fire# dar.ui.dom/*fire*]
                 (set! (~prop el#) (fn [ev#]
                                     (let [ev# (proc# ev#)]
                                       (when-not (nil? ev#)
                                         (listener# fire# ev#)))))))
         :off (fn [el# _#]
                (set! (~prop el#) nil))}))))
