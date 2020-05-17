(ns reseda.react.experimental
  (:require 
   [reseda.state :as rs]
   [reseda.react :as rr]
   ["react" :as react]))

(defn wrap-store [store]
  (let [ms (react/createMutableSource store (fn get-version [store]
                                              (-> store
                                                  .-backing
                                                  deref)))]
    ms))


(defn useStore [ms selector]
  (let [selector' (rr/useValue selector)
        subscribe (react/useCallback (fn [store cb]
                                       (let [k (rs/subscribe store selector' cb)]
                                         (fn unsub []
                                           (rs/unsubscribe store k))))
                                     #js [selector'])
        get-snapshot (react/useCallback #(rs/-get-value % selector') #js [selector'])
        value (react/useMutableSource ms get-snapshot subscribe)]
    value))

