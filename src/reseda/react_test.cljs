(ns reseda.react-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [clojure.string :as string]
            [kitchen-async.promise :as p]
            [reseda.state :as rs]
            [reseda.react :as rr]
            ["react" :as react]
            ["@testing-library/react" :as rtl]))

(js/console.log "Testing under React" (.-version react))

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


(defn act [cb]
  (rtl/act (fn [] (cb) js/undefined)))

(deftest use-store-keywords
  (testing "useStore can use selector keywords"
    (let [state (atom {:v 1})
          store (rs/new-store state)
          c (render-store store :v)]

      (rtl/render c)
      (is (= (-> (query-by-text "1")
                 node-text) "1"))
      (act #(swap! state update :v inc))
      (is (= (-> (query-by-text "2")
                 node-text) "2"))
      )))

(deftest use-store-vectors
  (testing "useStore can use vectors"
    (let [state (atom {:v {:v 1}})
          store (rs/new-store state)
          c (render-store store [:v :v])]
      (rtl/render c)
      (is (= (-> (query-by-text "1")
                 (node-text)) "1"))

      (act #(swap! state update-in [:v :v] inc))
      (is (= (-> (query-by-text "2")
                 (node-text)) "2")))))

(deftest use-store-equality
  (testing "clojure equality means we don't re-render"
    (let [state (atom {:v [1 2 3]})
          store (rs/new-store state)
          renders (atom 0)
          f (fn [v] (swap! renders inc) (str v))
          c (render-store store :v f)]
      (rtl/render c)
      (is (= @renders 1))
      (act #(swap! state assoc :v [1 2 3 4]))
      (is (= @renders 2))
      (act #(swap! state assoc :v (conj [1 2 3] 4)))
      (is (= @renders 2)))))

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

      (act #(swap! state assoc :v "b"))
      (is (= (-> (query-by-text "b")
                 node-text) "b"))
      (is (= @renders 2))
      (act #(swap! state assoc :v "B"))
      (is (= (-> (query-by-text "b")
                 node-text) "b"))
      (is (= @renders 2)))))


(deftest lifecycle
  (let [state (atom {:v 1})
        store (rs/new-store state)
        selector :v
        renders (atom 0)
        f (fn [v] (swap! renders inc) (str v))
        c (render-store store selector f)]
    (act #(swap! state update :v inc))
    (is (= @renders 0) "no renders until component is actually mounted")
    (let [rtl-fns (rtl/render c)]
      (is (= @renders 1) "first render")
      (act #(swap! state update :v inc))
      (is (= @renders 2) "render on update")
      (act #(.unmount rtl-fns))
      (is (= @renders 2) "no render on unmount")
      (act #(swap! state update :v inc))
      (is (= @renders 2) "no render after unmount"))))

(deftest lifecycle-no-multi-renders
  ;; this test crashes with infinite loops if the clojure
  ;; equality semantics aren't respected in useStore
  ;; we trigger this by creating a new CLJ object every
  ;; time in the selector
  (let [state (atom {:v {:foo :bar
                         :count 0}})
        store (rs/new-store state)
        selector (fn [state]
                   (let [{:keys [foo count]} (get state :v)]
                     {:foo foo
                      :count count}))
        renders (atom 0)
        f (fn [v] (swap! renders inc) (str v))
        c (render-store store selector f)]
    (act #(swap! state update-in [:v :count] inc))
    (is (= @renders 0) "no renders until component is actually mounted")
    (let [rtl-fns (rtl/render c)]
      (is (= @renders 1) "first render")
      (act #(swap! state update-in [:v :count] inc))
      (is (= @renders 2) "render on update")
      (act #(swap! state assoc-in [:v :foo] :bar))
      (is (= @renders 2) "no render when selector yields equivalent value")
      (act #(.unmount rtl-fns))
      (is (= @renders 2) "no render on unmount")
      (act #(swap! state update :v inc))
      (is (= @renders 2) "no render after unmount"))))
(defn make-promise []
  (let [fs (atom nil)
        p (js/Promise. (fn [resolve reject]
                         (reset! fs {:resolve resolve
                                     :reject reject})))]
    (assoc @fs :promise p)))

(defn SuspendImpl [js-props]
  (let [{:keys [susp f]} (.-props js-props)]
    (f @susp)))

(defn SuspenseRender [js-props]
  ($ "div" nil
     ($ react/Suspense #js {:fallback ($ "div" nil "FB")}
        ($ SuspendImpl js-props))))

(defn render-susp
  ([susp] (render-susp susp str))
  ([susp f]
   ($ SuspenseRender #js {:props {:susp susp
                                  :f f}})))

(deftest suspending-resolve
  (t/async
   done!
   (let [{:keys [promise resolve]} (make-promise)
         susp (rr/suspending-value promise)]
     (is (= false (rr/-resolved? susp)))
     (is (= false (rr/-rejected? susp)))
     (is (= false (realized? susp)))
     (try
       (deref susp)
       (catch :default x
         (is (= true (identical? x promise)))))

     (resolve :foo)
     (p/try
       promise
       (is (= true (rr/-resolved? susp)))
       (is (= false (rr/-rejected? susp)))
       (is (= true (realized? susp)))
       (is (= :foo (deref susp)))
       (p/finally
         (done!))))))

(deftest suspending-reject
  (t/async
   done!
   (let [{:keys [promise reject]} (make-promise)
         susp (rr/suspending-value promise)
         err (js/Error. "foo")]
     (is (= false (rr/-resolved? susp)))
     (is (= false (rr/-rejected? susp)))
     (is (= false (realized? susp)))

     (reject err)
     (p/try
       promise
       (p/catch js/Error x
         (is (= false (rr/-resolved? susp)))
         (is (= true (rr/-rejected? susp)))
         (is (= true (realized? susp)))
         (is (thrown-with-msg? js/Error
                               #"foo"
                               (deref susp)))
         (is (= err x)))
       (p/finally
         (done!))))))

(deftest suspending-noerror-resolve
  (t/async
   done!
   (let [{:keys [promise resolve]} (make-promise)
         susp (rr/suspending-value-noerror promise)]
     (is (= false (rr/-resolved? susp)))
     (is (= false (rr/-rejected? susp)))
     (is (= false (realized? susp)))
     (try
       (deref susp)
       (catch :default x
         (is (= true (identical? x promise)))))

     (resolve :foo)
     (p/try
       promise
       (is (= true (rr/-resolved? susp)))
       (is (= false (rr/-rejected? susp)))
       (is (= true (realized? susp)))
       (is (= :foo (deref susp)))
       (p/finally
         (done!))))))

(deftest suspending-noerror-reject
  (t/async
   done!
   (let [{:keys [promise reject]} (make-promise)
         susp (rr/suspending-value-noerror promise)
         err (js/Error. "foo")]
     (is (= false (rr/-resolved? susp)))
     (is (= false (rr/-rejected? susp)))
     (is (= false (realized? susp)))

     (reject err)

     (is (= false (rr/-resolved? susp)))
     (is (= false (rr/-rejected? susp)))
     (is (= false (realized? susp)))

     (try
       (deref susp)
       (catch :default x
         (is (= true (identical? x promise)))))
     (done!))))

(deftest suspending-integration
  (t/async
   done!
   (let [{:keys [promise resolve]} (make-promise)
         susp (rr/suspending-value promise)
         c (render-susp susp)]
     (rtl/render c)
     (is (= (-> (query-by-text "FB")
                node-text) "FB"))
     (p/try
       (rtl/act #(do (resolve "done")
                     promise))
       (rtl/waitFor #(query-by-text "done"))
       (is (= (-> (query-by-text "done")
                  node-text) "done"))
       (p/finally
         (done!))))))
