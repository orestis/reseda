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


;; Something fishy going on here with suspend. Investigate.
(defn useStore [store selector]
  ;; aim here is to subscribe to the store on mount,
  ;; unsubscribe on unmount, and always return the
  ;; current value of the store
  (let [init-v (rs/-get-value store selector)
        [init-v setState] (react/useState init-v)
        selector' (useValue selector)]
    (react/useEffect
     (fn []
       (let [k (rs/subscribe store selector setState)]
         (fn unsub []
           (rs/unsubscribe store k))))
     #js [store selector'])
    ;; return the current value
    init-v))


(deftype Suspending [loaded value promise error]
  IPrintWithWriter
  (-pr-writer [new-obj writer _]
    (write-all writer "#reseda.react.Suspending " (pr-str {:loaded loaded :value value :error error :promise promise})    ))
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