(defproject dar/ui "0.0.1-SNAPSHOT"
  :plugins [[dar/assets-lein "0.0.5"]]
  :profiles {:dev {:source-paths ["examples"]}}
  :target-path "build/target"
  :clean-targets ["build"]
  :assets {:build-dir "build/assets"
           :server-port 3000})
