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
(defn useStore [store selector]
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


(deftype Suspending [loaded value promise error]
  IPrintWithWriter
  (-pr-writer [new-obj writer _]
    (write-all writer "#reseda.react.Suspending " (pr-str {:loaded loaded :value value :error error :promise promise})))
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