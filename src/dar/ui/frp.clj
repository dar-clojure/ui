(ns dar.ui.frp)

(defmacro transform [& args]
  (let [name (if (symbol? (first args))
               [(first args)])
        [bindings & body] (if (symbol? (first args))
                            (next args)
                            args)
        bindings (partition 2 bindings)
        params (map first bindings)
        signals (map second bindings)]
    `((dar.ui.frp/lift (fn ~@name [~@params] ~@body)) ~@signals)))
