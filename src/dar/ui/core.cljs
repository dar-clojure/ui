(ns dar.ui.core)

(def ^:private counter (atom 0))

(defn new-uid []
  (swap! counter inc))

(defrecord Signal [name uid value fn inputs listeners])

(defrecord App [signals events outdate])

(declare get-signal-inputs)

(defn- update-signal [app {uid :uid :as signal}]
  (if ((:outdate app) uid)
    (let [app (update-in app [:outdate] disj uid)
          [input-vals app] (get-signal-inputs app signal)
          new-val (apply (:fn signal) input-vals)
          app (assoc-in app [:signals uid :value] new-val)
          app (if (event? new-val)
                (update-in app [:events] conj uid)
                app)]
      app)
    app))

(defn- get-input [app {uid :uid :as input} signal-uid]
  (let [app (update-signal app input)
        input (get-in app [:signals uid] input)
        input (update-in input [:listeners] conj signal-uid)]
    [(:value input) (assoc-in app [:signals uid] input)]))

(defn- get-signal-inputs [app {:keys [uid inputs]}]
  (reduce (fn [[vals app] input]
            (let [[val app] (get-input app input uid)]
              [(conj vals val) app]))
          [[] app]
          inputs))

(defn- compute-outdate [outdate signals changed-signal]
  (reduce (fn [outdate s]
            (add-outdate (conj outdate (:uid s))
                         signals
                         s))
          outdate
          (->> changed-signal :listeners (map #(get signals %)))))

(defn clear-events [app]
  (let [new-signals (reduce (fn [signals event-uid]
                              (assoc-in signals [event-uid value] nil))
                            (:signals app)
                            (:events app))]
    (assoc app :signals new-signals :events #{})))

(defn push [app {uid :uid :as signal} val]
  (let [{:keys [signals] :as app} (clear-events app)
        signal (or (get signals uid) signal)
        signal (assoc signal :value val)
        signals (assoc signals uid signal)
        outdate (compute-outdate outdate signals signal)]
    (loop [app (assoc app :signals signals :outdate outdate)]
      (if-let [outdate (-> app :outdate first)]
        (recur (update-signal app (-> app :signals (get outdate))))
        app))))
