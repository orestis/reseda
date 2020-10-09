(ns reseda.react-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [clojure.string :as string]
            [reseda.state :as rs]
            [reseda.react :as rr]
            ["react" :as react]
            ["@testing-library/react" :as rtl]))

(t/use-fixtures :each 
                {:after rtl/cleanup})

(defn $
  ([el]
   ($ el nil))
  ([el props & children]
   (apply react/createElement el props children)))

(defn ValueRender [js-props]
  (let [{:keys [store selector f]} (.-props js-props)
        value (rr/useStore store selector)]
    #_(js/console.log "store selector" store selector value)
    ($ "div" nil
       (f value))))

(defn render-store 
  ([store selector] (render-store store selector str))
  ([store selector f]
   ($ ValueRender #js {:props {:store store
                               :selector selector
                               :f f}})))

(defn query-by-text [x]
  (-> rtl/screen
      (.queryByText x)))

(defn node-text [node]
  (rtl/getNodeText node))


(deftest use-store-keywords
  (testing "useStore can use selector keywords"
    (let [state (atom {:v 1})
          store (rs/new-store state)
          c (render-store store :v)]

      (rtl/render c)
      (is (= (-> (query-by-text "1")
                 node-text) "1"))

      (swap! state update :v inc)
      (is (= (-> (query-by-text "2")
                 node-text) "2")))))

(deftest use-store-vectors
  (testing "useStore can use vectors"
    (let [state (atom {:v {:v 1}})
          store (rs/new-store state)
          c (render-store store [:v :v])]
      (rtl/render c)
      (is (= (-> (query-by-text "1")
                 (node-text)) "1"))

      (swap! state update-in [:v :v] inc)
      (is (= (-> (query-by-text "2")
                 (node-text)) "2")) )))

(deftest use-store-equality
  (testing "clojure equality means we don't re-render"
    (let [state (atom {:v [1 2 3]})
          store (rs/new-store state)
          renders (atom 0)
          f (fn [v] (swap! renders inc) (str v))
          c (render-store store :v f)]
      (rtl/render c)
      (is (= @renders 1))
      (swap! state assoc :v (conj [1 2] 3))
      (is (= @renders 1)))))

(deftest use-store-functions
  (testing "equality is based on the return value of the selector"
    (let [state (atom {:v "a"})
          store (rs/new-store state)
          selector (comp string/lower-case :v)
          renders (atom 0)
          f (fn [v] (swap! renders inc) (str v))
          c (render-store store selector f)]

      (rtl/render c)
      (is (= (-> (query-by-text "a")
                 node-text) "a"))
      (is (= @renders 1))

      (swap! state assoc :v "A")
      (is (= (-> (query-by-text "a")
                 node-text) "a"))
      (is (= @renders 1)))))


(deftest lifecycle 
  (let [state (atom {:v 1})
        store (rs/new-store state)
        selector :v
        renders (atom 0)
        f (fn [v] (swap! renders inc) (str v))
        c (render-store store selector f)]
      (swap! state update :v inc)
      (is (= @renders 0) "no renders until component is actually mounted")
      (let [rtl-fns (rtl/render c)]
        (js/console.log rtl-fns)
        (is (= @renders 1) "first render")
        (swap! state update :v inc)
        (is (= @renders 2) "render on update")
        (.unmount rtl-fns)
        (is (= @renders 2) "no render on unmount")
        (swap! state update :v inc)
        (is (= @renders 2) "no render after unmount"))))
