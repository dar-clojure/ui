(defproject dar/ui "0.0.1-SNAPSHOT"
  :plugins [[dar/assets-lein "0.0.1-SNAPSHOT"]]
  :profiles {:dev {:dependencies [[dar/assets "0.0.1-SNAPSHOT"]]}}
  :target-path "build/target"
  :clean-targets ["build"]
  :assets {:build-dir "build/assets"
           :pre-include ["dar/ui/components/css/normalize" "dar/ui/components/css/test"]
           :server-port 3000})
