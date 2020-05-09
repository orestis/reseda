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
  IDeref
  (-deref [this]
    (cond
      loaded value
      error  (throw error)
      :else (throw promise))))



(defn suspending-value [promise]
  (let [s (Suspending. false nil promise nil)]
    (.then promise
           (fn [value]
             (set! (.-value s) value)
             (set! (.-loaded s) true))
           (fn [error]
             (set! (.-error s) error)))
    s))