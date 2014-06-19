(ns dar.ui.lib.container)

(defmacro application [& body]
  `(binding [*app* (atom (->App {} (atom {}) nil :app))]
     ~@body
     @*app*))

(defmacro defapp [name & body]
  `(def ~name (application ~@body)))
