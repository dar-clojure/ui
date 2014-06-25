(ns dar.ui.frp)

(defmacro bind [& args]
  (let [name (if (symbol? (first args))
               [(first args)])
        [bindings & body] (if (symbol? (first args))
                            (next args)
                            args)
        bindings (partition 2 bindings)
        params (map first bindings)
        signals (map second bindings)]
    `(->Transform nil (new-uid) nil false #(apply (fn ~@name [~@params] ~@body) %) [~@signals])))

(defmacro event-bind [& args]
  `(as-event (bind ~@args)))

