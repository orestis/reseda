(ns reseda.react
  (:require
   [reseda.state :as rs]
   ["use-sync-external-store/shim" :refer [useSyncExternalStore]]
   ;["use-sync-external-store/shim/with-selector" :refer [useSyncExternalStoreWithSelector]]
   ["react" :as react]))

;; borrowed from hx
(defn- useValue
  "Caches `x`. When a new `x` is passed in, returns new `x` only if it is
  not structurally equal to the previous `x`.
  Useful for optimizing `<-effect` et. al. when you have two values that might
  be structurally equal by referentially different."
  [x]
  (let [-x (react/useRef x)]
    ;; if they are equal, return the prev one to ensure ref equality
    (let [x' (if (= x (.-current -x))
               (.-current -x)
               x)]
      ;; Set the ref to be the last value that was succesfully used to render
      (react/useEffect (fn []
                         (set! (.-current -x) x)
                         js/undefined)
                       #js [x'])
      x')))


(defn useStore
  "React hook that will re-render the component whenever the value returned by `selector` changes.
  NOTE: `selector` should be a stable function (not defined in-line, e.g. with
  useCallback) or keyword to avoid infinite re-renders. `selector` can also be a vector for `get-in`"
  [^rs/IStore store selector]
  (let [selector' (useValue selector)
        subscribe (react/useCallback (fn [cb]
                                       (let [k (rs/subscribe store selector' cb)]
                                         (fn unsub []
                                           (rs/unsubscribe store k))))
                                     #js [selector'])
        get-snapshot (react/useCallback #(rs/-get-value store selector') #js [selector'])
        value (useSyncExternalStore subscribe get-snapshot)]
    (react/useDebugValue value)
    value))


(defprotocol ISuspending
  (-resolved? [this] "Is the underlying promise resolved?")
  (-rejected? [this] "Is the underlying promise rejected?"))

(deftype Suspending [loaded value promise error]
  IPrintWithWriter
  (-pr-writer [new-obj writer _]
    (write-all writer "#reseda.react.Suspending " 
               (pr-str {:loaded loaded :value value :error error :promise promise})))
  ISuspending
  (-resolved? [this]
    (boolean (.-loaded this)))
  (-rejected? [this]
    (boolean (.-error this)))
  IPending
  (-realized? [this]
    (or (-resolved? this) (-rejected? this)))
  IDeref
  (-deref [this]
    (cond
      (.-loaded this) (.-value this)
      (.-error this)  (throw (.-error this))
      :else (throw (.-promise this)))))


(defn suspending-value 
  "Given a promise, return a Suspending that will suspend until the promise is resolved."
  [promise]
  (let [s (Suspending. false nil promise nil)]
    (.then promise
           (fn [value]
             (set! (.-value s) value)
             (set! (.-loaded s) true)
             value)
           (fn [error]
             (set! (.-error s) error)
             error))
    s))

(defn suspending-value-noerror [promise]
  (let [s (Suspending. false nil promise nil)]
    (.then promise
           (fn [value]
             (set! (.-value s) value)
             (set! (.-loaded s) true)
             value)
           (fn [error]
             error))
    s))


(defn suspending-image
  "Return a Suspending that will wrap an image URL. Can be used to make sure a Suspending component
   is shown only when the image is also shown. 
   
   Note: naive implementation, will never time out."
  [url]
  (let [img (js/Image.)
        p (js/Promise.
           (fn [resolve reject]
             (.addEventListener img "load"
                                (fn []
                                  (resolve url)))
             (.addEventListener img "error"
                                (fn []
                                  (reject url)))
             (set! (.-src img) url)))]
    (suspending-value p)))

(defn suspending-resolved 
  "Return a resolved Suspending that contains the value v.
  Different than just calling (suspending-reslved (js/Promise.resolve v))
  since it bypasses the Promise microTick queue."
  [v]
  (let [p (js/Promise.resolve v)
        s (Suspending. true v p nil)]
    s))

(defn suspending-error 
  "Return a resolved Suspening that contains the error e.
  See `suspending-resolved` for semantics."
  [e]
  (let [p (js/Promise.reject e)
        s (Suspending. false nil p e)]
    s))

(defn suspending-nil 
  "Convenience, returns a resolved Suspending that contains nil."
  []
  (suspending-resolved nil))


(defn suspending-forever 
  "Convenience, return a Suspending that will never resolve."
  []
  (let [p (js/Promise. (fn [_ _]))]
    (suspending-value p)))

(defn- useForceRender []
  (let [[_ set-state] (react/useState 0)
        force-render! #(set-state inc)]
    force-render!))

(defn- update-refs [^Suspending susp current-ref last-realized-ref is-pending force-render! mounted-ref]
  ;; the susp has changed, keep the current version around in a ref
  (set! (.-current current-ref) susp)
  (if (or (nil? susp) (realized? susp))
    ;; if it's nil or already realized, immediately bail out and let usual render take place
    (do
      (set! (.-current is-pending) false)
      (set! (.-current last-realized-ref) susp))   
    (do
      ;; otherwise, add a callback to the promise, to make us store it...
      (.then (.-promise susp)
             (fn [x]
               ;; make sure we only re-render if the latest susp is the one we subscribed to
               ;; and of course if we're still mounted
               (when (and (identical? susp (.-current current-ref))
                          (.-current mounted-ref))
                 (set! (.-current is-pending) false)
                 (set! (.-current last-realized-ref) susp)
                 (force-render!))
               x))
      (when (.-current mounted-ref)
        ;; and also re-render the component to turn on "is-pending"
        (set! (.-current is-pending) true)
        (force-render!)))))

(defn useCachedSuspending17 [^Suspending value]
  ;; keep track of the last realized suspending
  (let [last-realized-ref (react/useRef value)
        current-ref (react/useRef value)
        is-pending (react/useRef false)
        mounted-ref (react/useRef)
        force-render! (useForceRender)]
    (react/useLayoutEffect (fn []
                       (set! (.-current mounted-ref) true)
                       (fn [] (set! (.-current mounted-ref) false)))
                     #js [])
    (when-not
     (identical? (.-current current-ref) value)
      (update-refs value current-ref last-realized-ref is-pending force-render! mounted-ref))
    [(.-current last-realized-ref) (.-current is-pending)]))

(defn useCachedSuspending18 [^Suspending value]
  (let [deferred (react/useDeferredValue value)
        pending (not (identical? deferred value))]
    [deferred pending]))

(defn useCachedSuspending 
  "Return a vector of [deferred loading], with deferred being the last resolved Suspending 
   and loading an boolean showing if a new value is on the way.
   Components that `useCachedSuspending` will only suspend once, during the initial
   fetching of the data."
  [^Suspending value]
  (if (.-useDeferredValue react)
    (useCachedSuspending18 value)
    (useCachedSuspending17 value)))

(def ^:deprecated useSuspending 
  "Alias for useCachedSuspending" useCachedSuspending)

(defn deref* 
  "Deref v, iff it's a Suspending, otherwise return v."
  [v]
  (if (instance? Suspending v)
    (deref v)
    v))