(ns reseda.demo
  (:require
   [reseda.demo.util :refer [$]]
   [reseda.demo.bmi :as bmi]
   [reseda.demo.nasa-apod :as nasa-apod]
   [reseda.demo.lifecycle :as lifecycle]
   ["react-dom" :as react-dom]))


(def react-experimental?
;  false
  true
  #_(boolean react-dom/unstable_createRoot))


(defn react-root [el]
  (if react-experimental?
    (react-dom/unstable_createRoot el)
    el))

(defn react-render [root component]
  (if react-experimental?
    (.render root component)
    (react-dom/render component root)))

(defn Main []
  ($ "main" nil
     ($ "header" nil ($ "h1" nil "Reseda Demos"))
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