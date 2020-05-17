(ns reseda.react.experimental
  (:require 
   [reseda.state :as rs]
   ["react" :as react]))

(defn wrap-store [store]
  (let [ms (react/createMutableSource store (fn get-version [store]
                                              (-> store
                                                  .-backing
                                                  deref)))]
    ms))


(defn useStore [ms selector]
  (let [subscribe (fn [store cb]
                    (let [k (rs/subscribe store selector cb)]
                      #(rs/unsubscribe store k)))
        get-snapshot #(rs/-get-value % selector)
        value (react/useMutableSource ms get-snapshot subscribe)]
    value))

