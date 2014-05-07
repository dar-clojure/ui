(ns dar.ui.frp)

(def ^:private counter (atom 0))

(defn new-uid []
  (swap! counter inc))

(defprotocol ISignal
  (-touch [this app])
  (-update [this app])
  (-kill [this app]))

(defrecord App [signals listeners events outdate disposables rt])

(defrecord Signal [name uid value event?])

(defn probe [app signal]
  (-> app :signals (get (:uid signal)) :value))

(defn new-signal
  ([name value] (->Signal name (new-uid) value false))
  ([value] (new-signal nil value))
  ([] (new-signal nil)))

(defn as-event [s]
  (assoc s :event? true))

(defn new-event [& args]
  (as-event (apply new-signal args)))

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

(defn push* [app signal val]
  (if-let [s (get-signal app (:uid signal))]
    (let [s (assoc s :value val)]
      (-> app (assoc-signal s) (touch-listeners s)))
    app))

(defn push [app signal val]
  (-> (push* app signal val)
      (update)
      (clear-events)))

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

(defn pull-values [app signals l]
  (reduce (fn [[vals app] s]
            (let [[val app] (pull app s l)]
              [(conj vals val) app]))
          [[] app]
          signals))

(defn kill
  ([app {uid :uid}]
   (if-let [s (get-signal app uid)]
     (-> (-kill s app)
         (update-in [:signals] dissoc uid)
         (update-in [:listeners] dissoc uid))
     app))
  ([app {uid :uid :as s} listener]
   (let [listeners (-> app :listeners (get uid) (disj (:uid listener)))]
     (if (seq listeners)
       (assoc-in app [:listeners uid] listeners)
       (kill app s)))))

(defn kill-inputs [app signals listener]
  (reduce #(kill %1 %2 listener) app signals))

(defn kill-many [app signals]
  (reduce kill app signals))

(extend-protocol ISignal
  Signal
  (-update [this app] [this app])
  (-kill [_ app] app))

(defrecord Transform [name uid value event? f inputs]
  ISignal
  (-touch [this app] (touch-listeners app this))

  (-kill [this app] (kill-inputs app inputs this))

  (-update [this app] (let [[input-vals app] (pull-values app inputs this)
                            new-val (f input-vals)
                            this (assoc this :value new-val)]
                        [this app])))

(defn lift [function]
  (fn [& inputs]
    (->Transform nil (new-uid) nil false #(apply function %) inputs)))

(defn join
  ([xs] (->Transform nil (new-uid) nil false identity xs))
  ([x & xs] (join (cons x xs))))

(defrecord Switch [name uid value event? input current-signal]
  ISignal
  (-touch [this app] (touch-listeners app this))

  (-kill [this app] (-> app
                        (kill input this)
                        (kill current-signal this)))

  (-update [this app] (let [[new-signal app] (pull app input this)]
                        (if (= (:uid new-signal) (:uid current-signal))
                          (let [[new-val app] (pull app current-signal this)
                                this (assoc this :value new-val)]
                            [this app])
                          (let [app (kill app current-signal this)
                                [new-val app] (pull app new-signal this)
                                this (assoc this :value new-val :current-signal new-signal)]
                            [this app])))))

(defn switch [input]
  (->Switch nil (new-uid) nil false input nil))

(defrecord Foldp [name uid value fn input]
  ISignal
  (-touch [this app] (touch-listeners app this))

  (-kill [this app] (kill app input this))

  (-update [this app] (let [[input-val app] (pull app input this)]
                        (if (and (nil? input-val)
                                 (:event? input))
                          [this app]
                          (let [new-val (fn value input-val)
                                this (assoc this :value new-val)]
                            [this app])))))

(defn foldp [f init signal]
  (->Foldp nil (new-uid) init f signal))

(defrecord MapSwitch [name uid value input m sm sf reduce-fn init post]
  ISignal
  (-touch [this app] (touch-listeners app this))

  (-kill [this app] (-> app
                        (kill input this)
                        (kill-many (map #(nth % 2) (vals sm)))))

  (-update [this app] (let [[new-m app] (pull app input this)
                            [new-sm app] (if (identical? new-m m)
                                           [sm app]
                                           (loop [app (update-in app [:outdate] conj uid)
                                                  m (transient new-m)
                                                  sm sm
                                                  sm-seq (seq sm)]
                                             (if (seq sm-seq)
                                               (let [[k [v in out]] (first sm-seq)
                                                     new-v (get m k ::nil)]
                                                 (if (= ::nil new-v)
                                                   (recur (kill app out)
                                                          m
                                                          (dissoc sm k)
                                                          (next sm-seq))
                                                   (if (identical? new-v v)
                                                     (recur app
                                                            (dissoc! m k)
                                                            sm
                                                            (next sm-seq))
                                                     (recur (push* app in [k new-v])
                                                            (dissoc! m k)
                                                            (assoc sm k [new-v in out])
                                                            (next sm-seq)))))
                                               [(reduce (fn [sm [k v]]
                                                          (let [in (new-signal [k v])
                                                                out (sf in)]
                                                            (assoc sm k [v in out])))
                                                        sm
                                                        (persistent! m))
                                                (update-in app [:outdate] disj uid)])))
                            [new-val app] (reduce (fn [[acc app] [k [_ _ s]]]
                                                    (let [[v app] (pull app s this)]
                                                      [(reduce-fn acc [k v]) app]))
                                                  [init app]
                                                  new-sm)]
                        [(assoc this
                           :value (post new-val)
                           :sm new-sm
                           :m new-m)
                         app])))

(defn map-switch
  ([sf input]
   (->MapSwitch nil (new-uid) nil input nil {} sf conj nil identity))
  ([sf {:keys [reduce init post] :or {:reduce conj :init nil :post identity}} input]
   (->MapSwitch nil (new-uid) init input nil {} sf reduce init post)))

(defn automaton [initial-state commands commands-signal]
  (foldp (fn [state [cmd & args]]
           (apply (commands cmd) state args))
         initial-state
         commands-signal))

(defn to-event [signal]
  (->Transform nil (new-uid) nil true first [signal]))

;
; App
;

(defn new-app []
  (let [app (atom nil)]
    (reset! app (->App {} {} nil #{} {} app))
    app))

(defn push!
  ([app signal val]
   (swap! app push signal val)
   nil)
  ([app m]
   (swap! app (fn [app]
                (loop [app app
                       [[s v] & rest] (seq m)]
                  (if (seq rest)
                    (recur (push* app s v) rest)
                    (if s
                      (push app s v)
                      app)))))
   nil))

(defn watch! [app signal cb!]
  (let [[v state] (pull @app signal)]
    (reset! app state)
    (cb! v nil)
    (add-watch app (:uid signal) (fn [_ _ old new]
                                   (let [old (probe old signal)
                                         new (probe new signal)]
                                     (when-not (identical? new old)
                                       (cb! new old)))))))

(defn clear! [app signal]
  (remove-watch app (:uid signal))
  (reset! app (kill @app signal)))

(defrecord External [name uid value event? cb]
  ISignal
  (-update [this app] (let [{:keys [value dispose]} (cb (fn [v]
                                                          (if (nil? v)
                                                            (swap! (:rt app) (fn [app]
                                                                               (if-let [s (get-signal app uid)]
                                                                                 (-kill s app)
                                                                                 app)))
                                                            (push! (:rt app) s v))
                                                          nil))]
                        [(assoc this :value value) (if dispose
                                                     (assoc-in app [:disposables uid] dispose)
                                                     app)]))

  (-kill [_ app] (if-let [dispose! (-> app :disposables (get uid))]
                   (do
                     (dispose!)
                     (update-in app [:disposables] dissoc uid))
                   app)))

(defn new-signal* [f]
  (->External nil (new-uid) nil false f))

(defn new-event* [f]
  (as-event (new-signal* f)))
