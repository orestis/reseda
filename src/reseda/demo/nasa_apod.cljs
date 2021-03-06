(ns reseda.demo.nasa-apod
  (:require [reseda.demo.util :refer [$] :as util]            
            [reseda.state :as rs]
            [reseda.react :as rr]
            [reseda.react.experimental :as rre]
            [cljs-bean.core :refer [bean]]
            ["react" :as react]))

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

(defn change-date [d amount]
  (-> (.getTime d)
      (+ (* amount day-in-millis))
      (js/Date.)))

(defn fetch-apod [date]
  (-> date
      (query-url)
      (util/make-request)
      (.then (fn [text]
               (-> text (js/JSON.parse) (js->clj :keywordize-keys true))))
      (.then (fn [apod]
               (if (and (:url apod) (= "image" (:media_type apod)))
                 (assoc apod :suspense-url (rr/suspending-image (:url apod)))
                 apod)))
      (rr/suspending-value)))

(def now (js/Date.))
(defonce app-state 
  (atom {:date now
         :apod (fetch-apod now)}))

(defonce app-store (rs/new-store app-state))
(defonce ms-store (rre/wrap-store app-store))
(def the-store ms-store)
(def useStore rre/useStore)

(defn date-button-clicked [current-date direction]
  (let [new-date (change-date current-date direction)]
    (when (<= new-date (js/Date.))
      (swap! app-state assoc
             :date new-date
             :apod (fetch-apod new-date)))))

(defn DatePicker []
  (let [current-date (useStore the-store :date)
        [startTransition isPending] (react/unstable_useTransition #js {:timeoutMs 1500})]
    ($ "div" #js {:style #js {:display "flex"
                              :justifyContent "space-between"
                              :alignItems "center"}}
       ($ "button" #js {:onClick (fn []
                                   (startTransition #(date-button-clicked current-date -1)))}
          "Previous Day")
       ($ "strong" #js {:style (when isPending
                               #js {:opacity "50%"})}
          (str (date->query current-date)))
       ($ "button" #js {:onClick (fn []
                                   (startTransition #(date-button-clicked current-date +1)))}
          "Next Day"))))

(defn ApodMedia [props]
  (let [{:keys [suspense-url url media_type]} (bean props)]
    (case media_type
      "image" ($ "img" #js {:style #js {:width "100%"}
                            :src @suspense-url})
      "video" ($ "iframe" #js {:src url
                               :type "text/html"
                               :width "640px"
                               :height "360px"})
      ($ "pre" nil "Unknown media type: " media_type url)
)))



(defn ApodComponent [props]
  (let [apod (:apod (bean props))]
    ($ "article" #js {:style #js {:width "100%"}}
       ($ "h4" nil (:title apod))
       ($ "section" nil
          ($ "figure" nil
             ($ ApodMedia #js {:url (:url apod)
                               :suspense-url (:suspense-url apod)
                               :media_type (:media_type apod)})
             ($ "figcaption" nil
                (:date apod) " "
                "Copyright: " (:copyright apod)))
          ($ "p" nil (:explanation apod))))))

(defn ApodLoader []
  (let [apod (useStore the-store :apod)]
    ($ ApodComponent #js {:apod @apod})))


(defn NasaApodDemo []
  ($ "section" nil
     ($ "h2" nil "Astronomy Picture of the day")
     ($ "div" #js {}
        ($ DatePicker)
        ($ "hr")
        ($ react/Suspense #js {:fallback ($ "div" nil "Loading apod...")}
           ($ ApodLoader)))))

(comment
  (:apod @app-state)
  (swap! app-state assoc :date (js/Date.) :apod (fetch-apod now))

  (js/console.log @(:suspense-url @(:apod @app-state)))
  )