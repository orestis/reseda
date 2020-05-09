(ns reseda.demo
  (:require ["react" :as react]
            ["react-dom" :as react-dom]))

(defn $
  ([el]
   ($ el nil))
  ([el props & children]
   (apply react/createElement el props children)))

(defn Main []
  ($ "div" nil "Hello Reseda"))

(defonce react-root
  (delay (-> (js/document.getElementById "app")
             (react-dom/unstable_createRoot))))

(defn ^:dev/after-load start []
  (js/console.log "start")
  (.render @react-root ($ Main)))

(defn ^:export init []
  (js/console.log "init")
  (start))