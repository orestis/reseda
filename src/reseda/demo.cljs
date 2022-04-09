(ns reseda.demo
  (:require
   [reseda.demo.util :refer [$]]
   [reseda.demo.bmi :as bmi]
   [reseda.demo.nasa-apod-17 :as nasa-apod]
   [reseda.demo.lifecycle :as lifecycle]
   [reseda.demo.wikipedia :as wikipedia]
   [reseda.demo.pokemon :as pokemon]
   ;[reseda.demo.transitions :as transitions]
   [clojure.string :as string]
   ["react-dom" :as react-dom]
   ["react-dom/client" :as react-dom-client]))


(def react-18? (string/starts-with? (.-version react-dom)
                                    "18."))


(defn react-root [el]
  (if react-18?
    (react-dom-client/createRoot el)
    el))

(defn react-render [root component]
  (if react-18?
    (.render root component)
    (react-dom/render component root)))

(defn Main []
  ($ "main" nil
     ($ "header" nil ($ "h1" nil "Reseda Demos"))
     ($ "article" nil
        ($ pokemon/PokemonDemo))
     ($ "article" nil
        ($ wikipedia/WikiSearchDemo))
     #_
     ($ "article" nil
        ($ transitions/TransitionsDemo)
        ($ transitions/TransitionsDemoStore))
     ($ "hr")
     ($ "article" nil ($ lifecycle/LifecycleDemo))
     ($ "hr")
     ($ "article" nil
        ($ bmi/StoreDemo))
     ($ "hr")
     ($ "article" nil
        ($ nasa-apod/NasaApodDemo))))

(defonce root
  (delay (-> (js/document.getElementById "app")
             (react-root))))

(defn ^:dev/after-load start []
  (js/console.log "start")
  (react-render @root ($ Main)))

(defn ^:export init []
  (js/console.log "init")
  (start))