(ns reseda.demo.wikipedia
  (:require
   [reseda.demo.util :refer [$]]
   [reseda.state :refer [new-store]]
   [reseda.react :refer [useStore]]
   ["react" :as react]
   [cljs-bean.core :refer [->clj]]))

(defonce data (atom {}))
(defonce store (new-store data))

(def query-prefix "https://en.wikipedia.org/w/api.php?action=query&origin=*&list=search&format=json&srsearch=")

(defn fetch-wiki [query]
  (let [qs (str query-prefix (js/encodeURIComponent query))]
    (-> (js/fetch qs #js {:mode "cors"
                          :headers #js {"Content-Type" "application/json"}})
        (.then (fn [r]
                 (.json r)))
        (.then (fn [r]
                 (get-in (->clj r)
                         [:query :search]))))))

(defn fetch-wiki! [query]
  (swap! data assoc :results
         (reseda.react/suspending-value (fetch-wiki query))))

(defn SearchInput [props]
  ($ "div" nil
     ($ "input" #js {:type "search"
                     :value (str (useStore store :query))
                     :onChange (fn [e]
                                    (let [q (-> e .-target .-value)]
                                      (swap! data assoc :query q)
                                      (fetch-wiki! q)))})))

(defn SearchResults [props]
  (when-let [results (useStore store :results)]
    ($ "ul" nil
       (for [r @results]
         ($ "li" nil (:title r))))))

(defn WikiSearchDemo [_props]
  (useStore store :stable)
  ($ "section" nil
     ($ "h2" nil "Wikipedia Search")
     ($ SearchInput)
     ($ react/Suspense #js {:fallback ($ "div" nil "Fetching...")}
        ($ SearchResults))
     #_($ LifcycleCounters)))