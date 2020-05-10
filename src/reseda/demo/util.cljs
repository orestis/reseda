(ns reseda.demo.util
  (:require ["react" :as react]))

(defn $
  ([el]
   ($ el nil))
  ([el props & children]
   (apply react/createElement el props children)))