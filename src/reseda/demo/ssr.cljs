(ns reseda.demo.ssr
  (:require
   [reseda.demo.util :refer [$]]
   [cljs-bean.core :refer [->clj bean]]
   ["http" :as http]
   ["react" :as react]
   [reseda.react :refer [deref*]]
   ["react-dom/server" :as react-dom-server]))

(def query-prefix "https://pokeapi.co/api/v2/pokemon/")

(def DELAY 1000)

(defn fetch-pokemon [id]
  (let [qs (str query-prefix id)]
    (-> (js/fetch qs )
        (.then (fn [r]
                 (.json r)))
        (.then (fn [r]
                 (js/Promise. (fn [resolve _reject]
                                (js/setTimeout
                                 #(resolve (->clj r))
                                 DELAY))))))))


(defn use-first-render [set-pok*]
  (react/useEffect
      ;; useEffect won't fire in SSR, so this does nothing
   (fn []
     (set-pok* (->
                (fetch-pokemon 1)
                (reseda.react/suspending-value)))
     js/undefined)
   #js []))

(defn RenderPokemon [props]
  (let [{:keys [is-pending start-transition pok* set-pok*]} (bean props)
        pok (deref* pok*)]
    ($ "article" #js {}
       ($ "h4" #js {:style #js {:opacity (if is-pending 0.5 1)}} (:name pok))
       ($ "button" #js {:onClick #(start-transition
                                   (fn [] (set-pok*
                                           (->
                                            (fetch-pokemon (inc (:id pok)))
                                            (reseda.react/suspending-value)))))}
          "Next"))))

(defn PokemonDetail [props]
  (let [[pok* set-pok*] (react/useState (reseda.react/suspending-forever))
        [is-pending start-transition] (react/useTransition)]
    (use-first-render set-pok*)
    ($ react/Suspense #js {:fallback ($ "div" nil "Fetching pokemeon...")}
       ($ RenderPokemon #js {:pok* pok*
                             :is-pending is-pending
                             :start-transition start-transition
                             :set-pok* set-pok*}))))



(defn App [props]
  ($ "html" nil
     ($ "head" nil)
     ($ "body" nil
        ($ "h1" nil "Hello SSR!")
        ($ react/Suspense #js {:fallback ($ "div" nil "Fetching pokemeon...")}
           ($ PokemonDetail nil))
        ($ "p" nil "Pokemeon above"))))


(defn request-handler [^js req ^js res]
  (let [did-error (atom false)
        stream (atom nil)]
    (reset!
     stream
     (react-dom-server/renderToPipeableStream
      (App #js {})
      #js {:onError
           (fn [e]
             (reset! did-error true)
             (js/console.error "Error rendering" e))
           :onAllReady
           (fn []
             (js/console.log "ALL READY"))
           :onShellError
           (fn [err]
             (set! (.-statusCode res) 500)
             (.send res (str "ERROR:" err)))
           :onShellReady
           (fn []
             (set! (.-statusCode res) (if @did-error 500 200))
             (.setHeader res "Content-Type" "text/html")
             (.pipe ^js @stream res)
             (.end res))}))))

(defonce server-ref
  (volatile! nil))

(defn main [& args]
  (js/console.log "starting server")
  (let [server (http/createServer #(request-handler %1 %2))]

    (.listen server 3000
             (fn [err]
               (if err
                 (js/console.error "server start failed")
                 (js/console.info "http server running"))))

    (vreset! server-ref server)))

(defn start
  "Hook to start. Also used as a hook for hot code reload."
  []
  (js/console.warn "start called")
  (main))

(defn stop
  "Hot code reload hook to shut down resources so hot code reload can work"
  [done]
  (js/console.warn "stop called")
  (when-some [srv @server-ref]
    (.close srv
            (fn [err]
              (js/console.log "stop completed" err)
              (done)))))

(js/console.log "__filename" js/__filename)