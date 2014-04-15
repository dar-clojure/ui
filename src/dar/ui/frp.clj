(ns dar.ui.frp)

(def ^:private counter (atom 0))

(defn new-uid []
  (swap! counter inc))

(defprotocol ^:private ISignal
  (-update [this app])
  (-kill [this app gen]))

(defrecord App [signals events outdate])

(defrecord Signal [name uid value event? listeners])

(defn- get-signal [app uid]
  (-> app :signals (get uid)))

(defn- touch-listeners [app signal]
  (reduce (fn [{outdate :outdate :as app} uid]
            (if (outdate uid)
              app
              (touch-listeners (assoc app :outdate (conj outdate uid))
                               (get-signal app uid))))
            app
            (:listeners signal)))

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
    :events ()))

(defn- assoc-signal [app s]
  (-> app
      (assoc-in [:signals (:uid s)] s)
      (register-event s)))

(defn- dissoc-signal [app uid]
  (-> app (update-in [:signals] dissoc uid)))

(defn- update [{outdate :outdate :as app}]
  (if-let [uid (first outdate)]
    (let [app (assoc app :outdate (disj outdate uid))]
      (recur (if-let [s (get-signal app uid)]
               (second (-update s app))
               app)))
    app))

(defn push [app {uid :uid :as signal} val]
  (let [s (-> app :signals (get uid signal) (assoc :value val))]
    (-> app
        (assoc-signal s)
        (touch-listeners s)
        (update)
        (clear-events))))

(defn pull
  ([app signal] (pull app signal nil))
  ([{outdate :outdate :as app} {uid :uid :as signal} l]
   (let [curr (get-signal app uid)
         s (or curr signal)
         s (if l
             (update-in s [:listeners] conj (:uid l))
             s)
         [s app] (if-not curr
                   (-update s (assoc-in app [:signals uid] s))
                   (if (outdate uid)
                     (-update s (assoc app :outdate (disj outdate uid)))
                     (if (identical? s curr)
                       [s app]
                       [s (assoc-in app [:signals uid] s)])))]
     [(:value s) app])))

(defn pull-values [app signals l]
  (reduce (fn [[vals app] s]
            (let [[val app] (pull app s l)]
              [(conj vals val) app]))
          [[] app]
          signals))

(defn- kill [app {uid :uid :as signal} gen listener]
  (if (> uid gen)
    (-kill signal app gen)
    (if listener
      (if-let [listeners (get-in app [:signals uid :listeners])]
        (assoc-in app [:signals uid :listeners] (dissoc listeners (:uid listener)))
        app)
      app)))

(extend-protocol ISignal
  Signal
  (-update [this app] [this app])
  (-kill [{uid :uid} app gen] (update-in app [:signals] dissoc uid)))

(defrecord Transform [name uid value event? listeners fn inputs]
  ISignal
  (-kill [this app gen] (as-> app a
                              (dissoc-signal a uid)
                              (reduce #(kill %1 %2 gen this) a inputs)))

  (-update [this app] (let [[input-vals app] (pull-values app inputs this)
                            new-val (apply fn input-vals)
                            this (assoc this :value new-val)]
                        [this (assoc-signal app this)])))

(defrecord Switch [name uid value event? listeners input current-signal]
  ISignal
  (-kill [this app gen] (-> app
                            (dissoc-signal uid)
                            (kill input gen this)
                            (kill current-signal gen this)))

  (-update [this app] (let [[new-signal app] (pull app input this)]
                        (if (= (:uid new-signal) (:uid current-signal))
                          (let [[new-val app] (pull app current-signal this)
                                this (assoc this :value new-val)]
                            [this (assoc-signal app this)])
                          (let [app (kill app current-signal (::gen (meta current-signal)) this)
                                [new-val app] (pull app new-signal this)
                                this (assoc this :value new-val :current-signal new-signal)]
                            [this (assoc-signal app this)])))))

(defn as-event [s]
  (assoc s :event? true))

(defn new-signal
  ([name value] (->Signal name (new-uid) value false #{}))
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
                 #{}
                 function
                 inputs)))

(defn switch [factory & inputs]
  (let [input-sf (lift (fn [& args]
                         (let [gen (new-uid)]
                           (with-meta (apply factory args)
                             {::gen gen}))))
        input (apply input-sf inputs)]
    (->Switch nil (new-uid) nil false #{} input nil)))

(defn probe [app signal]
  (get-in app [:signals (:uid signal) :value]))

(defn alive? [app signal]
  (boolean (get-in app [:signals (:uid signal)])))
