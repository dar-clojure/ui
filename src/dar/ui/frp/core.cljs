(ns dar.ui.frp.core)

(def ^:private counter (atom 0))

(defn new-uid []
  (swap! counter inc))

(defprotocol ISignal
  (-touch [this app])
  (-update [this app])
  (-kill [this app gen]))

(defrecord App [signals listeners events outdate])

(defrecord Signal [name uid value event?])

(defn get-signal [app uid]
  (-> app :signals (get uid)))

(defn touch [{outdate :outdate :as app} signal]
  (let [new-outdate (conj outdate (:uid signal))]
    (if (identical? new-outdate outdate)
      app
      (-touch signal (assoc app :outdate new-outdate)))))

(defn touch-listeners [app signal]
  (reduce (fn [app uid]
            (touch app (get-signal app uid)))
          app
          (-> app :listeners (get (:uid signal)))))

(defn- register-event [app s]
  (if (:event? s)
    (update-in app [:events] conj (:uid s))
    app))

(defn- clear-events [app]
  (assoc app
    :signals (reduce (fn [m uid]
                       (if-let [s (get m uid)]
                         (assoc m uid (assoc s :value nil))
                         m))
                     (:signals app)
                     (:events app))
    :events nil))

(defn- assoc-signal [app s]
  (-> app
      (assoc-in [:signals (:uid s)] s)
      (register-event s)))

(defn- update [{outdate :outdate :as app}]
  (if-let [uid (first outdate)]
    (let [app (assoc app :outdate (disj outdate uid))]
      (recur (if-let [s (get-signal app uid)]
               (let [[s app] (-update s app)]
                 (assoc-signal app s))
               app)))
    app))

(defn push [app signal val]
  (let [s (-> app :signals (get (:uid signal) signal) (assoc :value val))]
    (-> app
        (assoc-signal s)
        (touch-listeners s)
        (update)
        (clear-events))))

(defn- set-conj [s v]
  (conj (or s #{}) v))

(defn pull
  ([app signal] (pull app signal nil))
  ([app {uid :uid :as signal} l]
   (let [curr (get-signal app uid)
         s (or curr signal)
         [s* app] (if-not curr
                    (-update s (assoc-in app [:signals uid] s))
                    (let [outdate (:outdate app)]
                      (if (outdate uid)
                        (-update s (assoc app :outdate (disj outdate uid)))
                        [s app])))
         app (if l
               (update-in app [:listeners uid] set-conj (:uid l))
               app)
         app (if (identical? s* s)
               app
               (assoc-signal app s*))]
     [(:value s*) app])))

(defn probe [app signal]
  (-> app :signals (get (:uid signal)) :value))

(defn pull-values [app signals l]
  (reduce (fn [[vals app] s]
            (let [[val app] (pull app s l)]
              [(conj vals val) app]))
          [[] app]
          signals))

(defn kill [app {uid :uid :as signal} gen listener]
  (if (> uid gen)
    (let [app (-> app
                  (update-in [:signals] dissoc uid)
                  (update-in [:listeners] dissoc uid))]
      (-kill signal app gen))
    (if listener
      (if-let [listeners (-> app :listeners (get (:uid listener)))]
        (assoc-in app [:listeners uid] (disj listeners (:uid listener)))
        app)
      app)))

(defn kill-many [app signals gen listener]
  (reduce #(kill %1 %2 gen listener) app signals))

(extend-protocol ISignal
  Signal
  (-update [this app] [this app])
  (-kill [{uid :uid} app gen] (update-in app [:signals] dissoc uid)))

(defrecord Transform [name uid value event? fn inputs]
  ISignal
  (-touch [this app] (touch-listeners app this))

  (-kill [this app gen] (kill-many app inputs gen this))

  (-update [this app] (let [[input-vals app] (pull-values app inputs this)
                            new-val (apply fn input-vals)
                            this (assoc this :value new-val)]
                        [this app])))

(defrecord Switch [name uid value event? input current-signal]
  ISignal
  (-touch [this app] (touch-listeners app this))

  (-kill [this app gen] (-> app
                            (kill input gen this)
                            (kill current-signal gen this)))

  (-update [this app] (let [[new-signal app] (pull app input this)]
                        (if (= (:uid new-signal) (:uid current-signal))
                          (let [[new-val app] (pull app current-signal this)
                                this (assoc this :value new-val)]
                            [this app])
                          (let [app (kill app current-signal (::gen (meta current-signal)) this)
                                [new-val app] (pull app new-signal this)
                                this (assoc this :value new-val :current-signal new-signal)]
                            [this app])))))

(defrecord Foldp [name uid value fn input]
  ISignal
  (-touch [this app] (touch-listeners app this))

  (-kill [this app gen] (kill app input gen this))

  (-update [this app] (let [[input-val app] (pull app input this)]
                        (if (and (nil? input-val)
                                 (:event? input))
                          [this app]
                          (let [new-val (fn value input-val)
                                this (assoc this :value new-val)]
                            [this app])))))

(defrecord DelayedChanges [name uid value event? input]
  ISignal
  (-touch [this app] app)

  (-kill [this app gen] (kill app input gen this))

  (-update [this app] (let [[new-val app] (pull app input this)]
                        (if (identical? new-val value)
                          [this app]
                          (let [this (assoc this :value new-val)
                                app (touch-listeners app this)]
                            [this app])))))

(defn as-event [s]
  (assoc s :event? true))

(defn new-signal
  ([name value] (->Signal name (new-uid) value false))
  ([value] (new-signal nil value))
  ([] (new-signal nil)))

(defn new-event [& args]
  (as-event (apply new-signal args)))

(defn lift [function]
  (fn [& inputs]
    (->Transform nil
                 (new-uid)
                 nil
                 false
                 function
                 inputs)))

(defn switch [factory & inputs]
  (let [input-sf (lift (fn [& args]
                         (let [gen (new-uid)]
                           (with-meta (apply factory args)
                             {::gen gen}))))
        input (apply input-sf inputs)]
    (->Switch nil (new-uid) nil false input nil)))

(defn foldp [f init signal]
  (->Foldp nil (new-uid) init f signal))

(defn join
  ([x] x)
  ([x & xs] (apply (lift (fn [& xs] xs)) x xs)))
