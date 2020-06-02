(ns reseda.demo.lifecycle
  (:require 
   [reseda.demo.util :refer [$]]
   [reseda.state :refer [new-store]]
   [reseda.react :refer [useStore]]))

(defonce data (atom {:counter 0
                     :stable nil}))
(defonce store (new-store data))

(defn change-data [k f]
  (js/setTimeout #(swap! data update k f) 0))

(defn CounterRenderer [_props]
  (let [c (useStore store :counter)]
    ($ "div" nil "Count is " c)))


(defn LifecycleButtons []
  ($ "div" nil
     ($ "button" #js
                  {:onClick (fn []
                              (change-data :counter inc))}
        "Increase")
     ($ "button" #js
                  {:onClick (fn []
                              (change-data :counter dec))}
        "Decrease")))

(defn LifcycleCounters []
  (let [c (useStore store :counter)]
    (for [i (range c)]
      ($ "div" #js {:key i}
         ($ CounterRenderer)))))

(defn LifecycleDemo [_props]
  (useStore store :stable)
  ($ "section" nil
     ($ "h2" nil "Lifecycle")
     ($ LifecycleButtons)
     ($ LifcycleCounters)))