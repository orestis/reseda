(ns reseda.demo.util
  (:require ["react" :as react]))

(defn $
  ([el]
   ($ el nil))
  ([el props & children]
   (apply react/createElement el props children)))

(defn make-request 
  ([url] (make-request url "GET"))
  ([url method]
   (let [r (js/XMLHttpRequest.)
         p (js/Promise. 
            (fn [resolve reject]
              (.addEventListener r "load" (fn []
                                            (when (= 4 (.-readyState r))
                                              (if (and (<= 200 (.-status r) 299))
                                                (resolve (.-response r))
                                                (reject r)))))
              (.open r method url)
              (.send r)))]
     
     p)))