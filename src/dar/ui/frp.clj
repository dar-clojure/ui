(ns dar.ui.frp)

(defmacro js-arguments [i]
  `(.. js/Array -prototype -slice (call (cljs.core/js-arguments) ~i)))

(defmacro bind [& args]
  (let [name (if (symbol? (first args))
               [(first args)])
        [bindings & body] (if (symbol? (first args))
                            (next args)
                            args)
        bindings (partition 2 bindings)
        params (map first bindings)
        signals (map second bindings)]
    `(dar.ui.frp.core/Transform. (fn ~@name [_# ~@params] ~@body) (array ~@signals))))

(defmacro bind* [& args]
  `(.asEvent (bind ~@args)))
