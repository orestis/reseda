(ns reseda.react
  (:require
   [reseda.state :as rs]
   ["react" :as react]))

;; borrowed from hx
(defn useValue
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


;; copy the approach of https://github.com/facebook/react/blob/master/packages/use-subscription/src/useSubscription.js#L71
(defn useStore
  "React hook that will re-render the component whenever the value returned by `selector` changes.
  NOTE: `selector` should be a stable function (not defined in-line, e.g. with
  useCallback) or keyword to avoid infinite re-renders. `selector` can also be a vector for `get-in`"
  [store selector]
  (let [selector (useValue selector)
        [state setState] (react/useState (fn [] {:store store
                                                 :selector selector
                                                 :value (rs/-get-value store selector)}))
        value-to-return (atom (:value state))]
    (when (or (not= store (:store state))
              (not= selector (:selector state)))
      (reset! value-to-return (rs/-get-value store selector))
      (setState {:store store
                 :selector selector
                 :value @value-to-return}))
    (react/useDebugValue @value-to-return)
    (react/useEffect
     (fn []
       (let [did-unsubscribe (atom false)
             check-for-updates
             (fn [value]
               (when-not @did-unsubscribe
                 (setState
                  (fn [prev-state]
                    (cond
                        ;; stale subscription; store or selector changed
                        ;; do not render for stale subscription, wait for
                        ;; another render to be scheduled
                      (or (not= store (:store prev-state))
                          (not= selector (:selector prev-state)))                      
                      prev-state
                      ;; value is the same (store handles equality so here just check for identical)
                      (identical? (:value prev-state) value)
                      prev-state

                      :else (assoc prev-state :value value))))))

             k (rs/subscribe store selector check-for-updates)]
         (check-for-updates @value-to-return)
         (fn unsubscribe []
           (reset! did-unsubscribe true)
           (rs/unsubscribe store k))))
     #js [store selector])
    @value-to-return))


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


(defn suspending-value [promise]
  (let [s (Suspending. false nil promise nil)]
    (.then promise
           (fn [value]
             (set! (.-value s) value)
             (set! (.-loaded s) true)
             value)
           (fn [error]
             (set! (.-error s) error)))
    s))

(defn suspending-image [url]
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


(defn- useForceRender []
  (let [[_ set-state] (react/useState 0)
        force-render! #(set-state inc)]
    force-render!))

(def __id (atom 0))
(defn- next-id []
  (let [id (swap! __id inc)]
    id))

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

(defn useSuspending 
  "Given a Suspending object, return the version of it that was last realized, and a boolean
   that indicates whether a new value is on the way. Can be used for a similar effect to useTransition"
  [^Suspending value]
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
