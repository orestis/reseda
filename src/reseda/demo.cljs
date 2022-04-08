(ns reseda.demo
  (:require
   [reseda.demo.util :refer [$]]
   [reseda.demo.bmi :as bmi]
   ;[reseda.demo.nasa-apod :as nasa-apod]
   [reseda.demo.lifecycle :as lifecycle]
   ;[reseda.demo.transitions :as transitions]
   ["react-dom" :as react-dom]))


(def react-18? false)


(defn react-root [el]
  (if react-18?
    (react-dom/createRoot el)
    el))

(defn react-render [root component]
  (if react-18?
    (.render root component)
    (react-dom/render component root)))

(defn Main []
  ($ "main" nil
     ($ "header" nil ($ "h1" nil "Reseda Demos"))
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
     #_
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