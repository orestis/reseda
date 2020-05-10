(ns reseda.demo
  (:require
   [reseda.demo.util :refer [$]]
   [reseda.demo.store :as demo-store]
   ["react-dom" :as react-dom]))



(defn Main []
  ($ "div" nil 
     ($ "h1" nil "Reseda Demos")
     ($ demo-store/StoreDemo)))

(defonce react-root
  (delay (-> (js/document.getElementById "app")
             (react-dom/unstable_createRoot))))

(defn ^:dev/after-load start []
  (js/console.log "start")
  (.render @react-root ($ Main)))

(defn ^:export init []
  (js/console.log "init")
  (start))