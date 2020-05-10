(ns reseda.state
  (:require [nano-id.core :refer [nano-id]]))

;; TODO: expose subscriptions
;; TODO: timing?
;; TODO: custom equality
;; TODO: caching / deduplication
(defprotocol IStore
  (-trigger-subs [this old-state new-state])
  (-get-value [this selector])

  (destroy [this])
  (subscribe [this selector on-change])
  (unsubscribe [this k]))

;; TODO: no need for atom for subs, could use
;; a mutable field for performance
(deftype Store [backing subs watch-key]
  IStore
  (destroy [this]
    (reset! subs {})
    (remove-watch backing watch-key))
  
  (-trigger-subs [this old-state new-state]
    (doseq [[selector on-change] (vals @subs)]
      (let [oldv (selector old-state)
            newv (selector new-state)]
        (when-not (= oldv newv)
          (on-change newv)))))

  (-get-value [this selector]
    (selector @backing))

  (subscribe [this selector on-change]
    (let [k (random-uuid)]
      (swap! subs assoc k [selector on-change])
      k))

  (unsubscribe [this k]
    (swap! subs dissoc k)
    k))


(defn new-store [backing]
  (let [watch-key (nano-id)
        store (Store. backing (atom {}) watch-key)
        watch-fn (fn [_ _ old-state new-state]
                   (-trigger-subs store old-state new-state))]
    (add-watch backing watch-key watch-fn)
    store))
