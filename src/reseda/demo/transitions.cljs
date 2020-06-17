(ns reseda.demo.transitions
  (:require
   [reseda.demo.util :refer [$] :as util]
   [reseda.state :as rs]
   [reseda.react :as rr]
   [reseda.react.experimental :as rre]
   [cljs-bean.core :refer [bean]]
   ["react" :as react]))


(defn fetch-data [section]
  (-> (util/timeout-promise (str (:title section) " data") 2000)
      (rr/suspending-value)))

(def SUSPENSE-CONFIG #js {:timeoutMs 3000})

(defn Button [props]
  (let [[startTransition isPending] (react/unstable_useTransition SUSPENSE-CONFIG)]
    ($ "button" #js {:onClick (fn [] ((.-onClick props) startTransition))
                     :style #js {:fontWeight (if (.-active props) "bold" "normal")}}
       ($ (if isPending "em" "span") nil
          (.-title props)))))


(def sections
  [{:id 1 :title "First"}
   {:id 2 :title "Second"}
   {:id 3 :title "Third"}])

(defn Data [props]
  ($ "div" nil @(.-data props)))

(defn TransitionsDemo []
  (let [[active setActive] (react/useState 1)
        [data setData] (react/useState (fetch-data (first sections)))]
    ($ "section" nil
       ($ "h2" nil "useTransition setState")
       ($ "div" #js {}        
          (for [section sections]
            ($ Button #js {:title (:title section)
                           :active (= active (:id section))
                           :key (:id section)
                           :onClick (fn [t]
                                      (setActive (:id section))
                                      (t
                                       #(setData (fetch-data section)))
                                      )}))
          ($ "hr")          
          ($ react/Suspense #js {:fallback ($ "div" nil "Loading...")}
             ($ Data #js {:data data}))))))

(defonce app-state
  (atom {:active 1
         :data (fetch-data (first sections))}))

(defonce app-store (rs/new-store app-state))
(defonce ms-store (rre/wrap-store app-store))
(def the-store ms-store)
(def useStore rre/useStore)

(defn TransitionsDemoStore []
  (let [active (useStore the-store :active)
        data (useStore the-store :data)
        setActive #(swap! app-state assoc :active %)
        setData #(swap! app-state assoc :data %)]
    ($ "section" nil
       ($ "h2" nil "useTransition Store")
       ($ "div" #js {}
          (for [section sections]
            ($ Button #js {:title (:title section)
                           :active (= active (:id section))
                           :key (:id section)
                           :onClick (fn [t]
                                      (setActive (:id section))
                                      (t
                                       #(setData (fetch-data section))))}))
          ($ "hr")
          ($ react/Suspense #js {:fallback ($ "div" nil "Loading...")}
             ($ Data #js {:data data}))))))