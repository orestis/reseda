(ns reseda.demo.nasa-apod
  (:require [reseda.demo.util :refer [$] :as util]            
;            [reseda.state :refer [new-store] :as re-store]
            [reseda.react :refer [suspending-value suspending-image just-suspend new-store-2 useStore2]]
            [cljs-bean.core :refer [bean]]
            ["react" :as react]))


(defonce app-state 
  (atom {:date (js/Date.)
         :apod (just-suspend)}))

(defonce app-store (new-store-2 app-state))

(def api-key "HquDsZLQArdVX1iaFoZGnWMD1AvoOkUEhlTtboCe" #_"DEMO_KEY")

(defn date->query [date]
  (let [d (.getDate date)
        m (-> (.getMonth date)
              inc)              
        y (.getFullYear date)]
    (str y "-" (when (< m 10) "0") m "-" (when (< d 10) "0") d)))

(defn query-url [date]
  (str "https://api.nasa.gov/planetary/apod?api_key="
       api-key
       "&date=" (date->query date)))

(def day-in-millis (* 24 60 60 1000))

(defn change-date [d amount op]
  (-> (.getTime d)
       (op (* amount day-in-millis))
       (js/Date.)))

(defn fetch-apod [date]
  (-> date
      (query-url)
      (util/make-request)
      (.then (fn [text]
               (js/console.log "received apod")
               (-> text (js/JSON.parse) (js->clj :keywordize-keys true))))
      (.then (fn [apod]
               (js/console.log "processing apod")
               (if (and (:url apod) (= "image" (:media_type apod)))
                 (assoc apod :suspense-url (suspending-image (:url apod)))
                 apod)))
      (suspending-value)))

(defn current-date-changed [date]
  (js/console.log "current date-changed")
  (let [apod (fetch-apod date)]
    (js/console.log "current date changed, new apod" apod)
    (swap! app-state assoc :apod apod)))

#_(defonce subscriptions 
  (reseda.state/subscribe app-store :date current-date-changed))

(defn DatePicker []
  (let [current-date (useStore2 app-store :date)]
    ($ "div" nil
       ($ "button" #js {:onClick (fn [] 
                                   (let [new-date (change-date current-date 1 -)
                                         new-apod (fetch-apod new-date)]
                                     (swap! app-state assoc
                                            :date new-date
                                            :apod new-apod
                                            )))}
          "Previous Day")
       ($ "span" nil (str (date->query current-date)))
       #_($ "button" #js {:onClick (fn [] (swap! app-state update :date
                                               #(change-date % 1 +)))}
          "Next Day"))))

(defn ApodMedia [props]
  (let [{:keys [suspense-url url media_type]} (bean props)]
    (js/console.log "loading apod media" suspense-url url media_type)
    (case media_type
      "image" ($ "img" #js {:style #js {:width "100%"}
                            :src url #_@suspense-url})
      "video" ($ "iframe" #js {:src url
                               :type "text/html"
                               :width "640px"
                               :height "360px"})
      ($ "pre" nil "Unknown media type: " media_type url)
)))



(defn ApodComponent [props]
  (js/console.log "rendering apod component" props)
  (let [apod (:apod (bean props))]
    ($ "div" #js {:style #js {:width "100%"}}
       ($ "h3" nil (:title apod))
       ($ ApodMedia #js {:url (:url apod)
                         :suspense-url (:suspense-url apod)
                         :media_type (:media_type apod)})
       ($ "h6" nil "Copyright: " (:copyright apod))
       ($ "p" nil (:explanation apod)))))

(defn ApodLoader []
  (js/console.log "Rendering apod loader")
  (let [apod (useStore2 app-store :apod)]
    ($ ApodComponent #js {:apod @apod})))


(defn NasaApodDemo []
  ($ "div" nil
     ($ "h1" nil "Astronomy Picture of the day")
     ($ "div" #js {:style #js {:width "30em"}}
        ($ DatePicker)
        ($ react/Suspense #js {:fallback ($ "div" nil "Loading apod...")}
           ($ ApodLoader)))))

(comment
  (:apod @app-state)
  (js/console.log @(:suspense-url @(:apod @app-state)))
  )