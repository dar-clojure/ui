(ns dar.ui.macro
  (:require [clojure.string :as string]))

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

(def ^:private dom-elements
  '[a abbr address area article aside audio b base bdi bdo big blockquote body br
    button canvas caption cite code col colgroup data datalist dd del details dfn
    div dl dt em embed fieldset figcaption figure footer form h1 h2 h3 h4 h5 h6
    head header hr html i iframe img input ins kbd keygen label legend li link main
    map mark menu menuitem meta meter nav noscript object ol optgroup option output
    p param pre progress q rp rt ruby s samp script section select small source
    span strong style sub summary sup table tbody td textarea tfoot th thead time
    title tr track u ul var video wbr
    circle g line path polygon polyline rect svg text])

(defn- render-dom-el-macro [tag [attrs & children]]
  (let [children (if (vector? (first children))
                   (ffirst children)
                   `(list ~@children))
        tag (keyword (name tag))]
    `(dar.ui.dom/->Element ~tag ~attrs ~children)))

(defn- gen-dom-element-macro [tag]
  `(defmacro ~(symbol (string/upper-case tag)) [& args#]
     (render-dom-el-macro '~tag args#)))

(defmacro ^:private gen-dom-element-macroses []
  `(do ~@(map gen-dom-element-macro dom-elements)))

(gen-dom-element-macroses)
