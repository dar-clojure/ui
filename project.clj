(defproject dar/ui "0.0.1-SNAPSHOT"
  :plugins [[dar/assets-lein "0.0.1-SNAPSHOT"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]
                                  [dar/assets "0.0.1-SNAPSHOT"]]
                   :source-paths ["examples"]}}
  :target-path "build/target"
  :clean-targets ["build"]
  :assets {:build-dir "build/assets"
           :server-port 3000})
