(ns reseda.demo.bmi
  (:require
   [cljs-bean.core :refer [bean]]
   [reseda.demo.util :refer [$]]
   [reseda.state :refer [new-store]]
   [reseda.react :refer [useStore]]))


(defn calc-bmi [{:keys [height weight bmi] :as data}]
  (let [h (/ height 100)]
    (if (nil? bmi)
      (assoc data :bmi (/ weight (* h h)))
      (assoc data :weight (* bmi h h)))))

;; This is the "backing store" atom. It's a plain old Clojure atom, you can
;; do whatever you want with it.
(defonce bmi-data (atom (calc-bmi {:height 180 :weight 80})))

;; This is a store backed by the above atom. It provides a way
;; to subscribe to "selectors" via callback functions.
(defonce bmi-store (new-store bmi-data))

(defn Slider [props]
  (let [{:keys [param value min max invalidates]} (bean props)]
    ($ "input" #js {:type "range"
                    :value value
                    :min min
                    :max max
                    :style #js {:width "100%"}
                    :onChange (fn [e]
                                (let [new-value (js/parseInt (.. e -target -value))]
                                  ;; At the lowest level of abstraction, we can just
                                  ;; manipulate the backing atom directly.
                                  (swap! bmi-data
                                         (fn [data]
                                           (-> data
                                               (assoc param new-value)
                                               (dissoc invalidates)
                                               calc-bmi)))))})))

(defn BmiComponent [_props]
  ;; `useStore` is the react-aware subscription hook, to observe
  ;; the Reseda store. In this example we observe the entire store
  ;; so the selector is identity.
  (let [{:keys [weight height bmi]} (useStore bmi-store identity)
        [color diagnose] (cond
                           (< bmi 18.5) ["orange" "underweight"]
                           (< bmi 25) ["inherit" "normal"]
                           (< bmi 30) ["orange" "overweight"]
                           :else ["red" "obese"])]
    ($ "form" nil       
       ($ "fieldset" nil
          ($ "legend" nil "BMI calculator")          
          ($ "label" nil "Height: " (int height) "cm")
          ($ Slider #js {:param :height
                         :value height
                         :min 100
                         :max 220
                         :invalidates :bmi})
          ($ "div" nil 
             ($ "label" nil "Weight: " (int weight) "kg")
             ($ Slider #js {:param :weight
                            :value weight
                            :min 30
                            :max 150
                            :invalidates :bmi}))
          ($ "div" nil 
             ($ "label" nil "BMI: " (int bmi) " ")
             ($ "span" #js {:style #js {:color color}} diagnose)
             ($ Slider #js {:param :bmi
                            :value bmi
                            :min 10
                            :max 50
                            :invalidates :weight}))))))

(defn StoreDemo []
  ($ "div" nil
     ($ "h2" nil "BMI Calculator")
     ($ "div" #js {:style #js {:width "30em"}}
        ($ BmiComponent))))

(comment
  @(.-subs bmi-store))