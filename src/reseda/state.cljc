(ns reseda.state)


(defn nano-id []
  (str (random-uuid)))

;; TODO: expose subscriptions
;; TODO: timing?
;; TODO: custom equality
;; TODO: caching / deduplication
(defprotocol IStore
  (-trigger-subs [this old-state new-state])
  (-get-value [this selector] [this backing selector])

  (destroy [this] "Remove all references to subscriptions and the underlying watchable.")
  (subscribe [this selector on-change]
    "Subscribe to changes in the underlying watchable, using a selector function.
             Whenever the old value and the new value (as returned by the selector) differ (based on Clojure equality),
             call on-change with the new value. Return a unique key that can be passed to `unsubscribe`")
  (unsubscribe [this k] "Destroy the subscription under `key`"))

;; TODO: no need for atom for subs, could use
;; a mutable field for performance
(deftype Store [backing subs watch-key]
  IStore
  (destroy [this]
    (reset! subs {})
    (remove-watch backing watch-key))

  (-trigger-subs [this old-state new-state]
    (doseq [[selector on-change] (vals @subs)]
      (let [oldv (-get-value this old-state selector)
            newv (-get-value this new-state selector)]
        (when-not (= oldv newv)
          (on-change newv)))))

  (-get-value [this selector]
    (-get-value this @backing selector))

  (-get-value [this x selector]
    (if (vector? selector)
      (get-in x selector)
      (selector x)))

  (subscribe [this selector on-change]
    (let [k (nano-id)]
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
