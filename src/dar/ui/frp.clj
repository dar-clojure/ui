(ns dar.ui.frp)

(def ^:private counter (atom 0))

(defn new-uid []
  (swap! counter inc))

(defprotocol ISignal
  (-update [this app])
  (-kill [this app gen]))

(defrecord Signal [name uid value listeners])

(defn- touch-listeners [app signal]
  (reduce (fn [app s]
            (if (:outdate? s)
              app
              (touch-listeners (assoc-in app [(:uid s) :outdate?] true)
                               s)))
          app
          (map #(get app %) (:listeners signal))))

(defn push [app {uid :uid :as signal} val]
  (let [s (get app uid signal)
        s (assoc s :value val)
        app (assoc app uid s)]
    (touch-listeners app s)))

(defn probe
  ([app signal] (probe app signal nil))
  ([app {uid :uid :as signal} l]
   (let [init (get app uid)
         s (or init signal)
         s (if l
             (update-in s [:listeners] conj (:uid l))
             s)
         outdate? (:outdate? s)
         s (if outdate?
             (assoc s :outdate? false)
             s)
         app (if (identical? init s)
               app
               (assoc app uid s))]
     (if outdate?
       (-update s app)
       [(:value s) app]))))

(defn probe-values [app signals l]
  (reduce (fn [[vals app] s]
            (let [[val app] (probe app s l)]
              [(conj vals val) app]))
          [[] app]
          signals))

(defn kill [app {uid :uid :as signal} gen listener]
  (if (> uid gen)
    (-kill signal app gen)
    (if listener
      (if-let [listeners (get-in app [uid :listeners])]
        (assoc-in app [uid :listeners] (dissoc listeners (:uid listener)))
        app)
      app)))

(extend-protocol ISignal
  Signal
  (-kill [this app gen] (dissoc app (:uid this))))

(defrecord Transform [name uid value listeners outdate? fn inputs]
  ISignal
  (-kill [this app gen] (as-> app a
                              (dissoc a uid)
                              (reduce #(kill %1 %2 gen this) a inputs)))

  (-update [this app] (let [[input-vals app] (probe-values app inputs this)
                            new-val (apply fn input-vals)]
                        [new-val (assoc-in app [uid :value] new-val)])))

(defrecord Switch [name uid value listeners outdate? input current-signal]
  ISignal
  (-kill [this app gen] (-> app
                            (dissoc uid)
                            (kill input gen this)
                            (kill current-signal gen this)))

  (-update [this app] (let [[new-signal app] (probe app input this)]
                        (if (= (:uid new-signal) (:uid current-signal))
                          (let [[new-val app] (probe app current-signal this)]
                            [new-val (assoc-in app [uid :value] new-val)])
                          (let [app (kill app current-signal (::gen (meta current-signal)) this)
                                [new-val app] (probe app new-signal this)]
                            [new-val (update-in app [uid] #(assoc %
                                                             :value new-val
                                                             :current-signal new-signal))])))))

(defn new-input
  ([name value] (->Signal name (new-uid) value #{}))
  ([value] (new-input nil value))
  ([] (new-input nil)))

(defn lift [function]
  (fn [& inputs]
    (->Transform nil
                 (new-uid)
                 nil
                 #{}
                 true
                 function
                 inputs)))

(defn switch [factory & inputs]
  (let [input-sf (lift (fn [& args]
                         (let [gen (new-uid)]
                           (with-meta (apply factory args)
                             {::gen gen}))))
        input (apply input-sf inputs)]
    (->Switch nil (new-uid) nil #{} true input nil)))
