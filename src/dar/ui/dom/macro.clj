(ns dar.ui.dom.macro)

(defmacro event [type & [proc]]
  (let [prop (symbol (str ".-on" (name type)))
        proc (or proc `identity)]
    `(let [proc# ~proc]
       (dar.ui.dom/install-plugin!
        ~(keyword (str "ev-" (name type)))
        {:on (fn [el# listener#]
               (let [fire# dar.ui.dom/*fire*]
                 (set! (~prop el#) (fn [ev#]
                                     (listener# fire# (proc# ev#))))))
         :off (fn [el# _#]
                (set! (~prop el#) nil))}))))
