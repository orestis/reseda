(ns reseda.demo.wikipedia
  (:require ["react" :as react]
            [cljs-bean.core :refer [->clj bean]]
            [clojure.string :as string]
            [reseda.demo.util :refer [$]]
            [reseda.react :refer [useStore deref*]]
            [reseda.state :refer [new-store]]))

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
  (let [start-transition (:start-transition (bean props))]
    ($ "div" nil
       ($ "input" #js {:type "search"
                       :value (str (useStore store :query))
                       :onChange (fn [e]
                                   (let [q (-> e .-target .-value)]
                                     (swap! data assoc :query q)
                                     #_(fetch-wiki! q)
                                     (start-transition #(fetch-wiki! q))))}))))

(defn SearchResults [props]
  (let [results (react/useDeferredValue (useStore store :results))
        loading (:loading (bean props))
        query (useStore store :query)
        results (deref* results)]
      (if (and (not (seq results)) 
               (not (string/blank? query)))
          ($ "div" nil "No results")
          ($ "ul" #js {}
             (for [r results]
                  ($ "li" #js {:key (:title r)
                               :style #js {:opacity (if loading 0.5 1.0)}}
                     (:title r)))))))

(defn WikiSearchDemo [_props]
  (let [[is-pending startTransition] (react/useTransition)]
    ($ "section" nil
       ($ "h2" nil "Wikipedia Search")
       ($ SearchInput #js {:start-transition startTransition})
       ($ react/Suspense #js {:fallback ($ "div" nil "Fetching...")}
          ($ SearchResults #js {:loading is-pending})))))