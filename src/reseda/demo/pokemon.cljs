(ns reseda.demo.pokemon
  (:require ["react" :as react]
            [cljs-bean.core :refer [->clj bean]]
            [clojure.string :as string]
            [reseda.demo.util :refer [$]]
            [reseda.react :refer [useStore deref*]]
            [reseda.state :refer [new-store]]))

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


(defonce first-pokemon (reseda.react/suspending-value (fetch-pokemon 1)))

(defn RenderPokemon [props]
(let [{:keys [is-pending start-transition pok set-pok*]} (bean props)]
  ($ "article" #js {}
     ($ "h4" #js {:style #js {:opacity (if is-pending 0.5 1)}} (:name pok))
     ($ "button" #js {:onClick #(start-transition
                                 (fn [] (set-pok*
                                         (->
                                          (fetch-pokemon (inc (:id pok)))
                                          (reseda.react/suspending-value)))))}
        "Next")))
  )

(defn PokemonDetail [props]
  (let [[pok* set-pok*] (react/useState first-pokemon)
        [is-pending start-transition] (react/useTransition)
        pok (deref* pok*)]
      ($ RenderPokemon #js {:pok pok 
                            :is-pending is-pending
                            :start-transition start-transition
                            :set-pok* set-pok*})
    ))

(defonce data (atom first-pokemon))
(defonce store (new-store data))

(defn PokemonDetailStore [props]
  (let [pok* (reseda.react/useStore store identity)
        set-pok* (fn [x] (reset! data x))
        [is-pending start-transition] (react/useTransition)
        pok (deref* pok*)]
    ($ RenderPokemon #js {:pok pok
                          :is-pending is-pending
                          :start-transition start-transition
                          :set-pok* set-pok*})))

(defn PokemonDetailStoreDeferred [props]
  (let [pok* (reseda.react/useStore store identity)
        set-pok* (fn [x] (reset! data x))
        deferred-pok* (react/useDeferredValue pok*)
        is-pending (not (identical? pok* deferred-pok*))
        start-transition apply
        pok (deref* deferred-pok*)]
    ($ RenderPokemon #js {:pok pok
                          :is-pending is-pending
                          :start-transition start-transition
                          :set-pok* set-pok*})))

(defn PokemonDemo [_props]
  ($ "section" nil
     ($ "h2" nil "Pokemon Transitions")
     ($ "aside" nil "Inspired by https://www.youtube.com/watch?v=Kd0d-9RQHSw")
     ($ "hr")
     ($ "h3" nil "Pokemon Demo (useState + useTransition)")
     ($ react/Suspense #js {:fallback ($ "div" nil "Fetching pokemeon...")}
        ($ PokemonDetail))
     ($ "hr")
     ($ "h3" nil "Pokemon Demo (useStore + useDeferredValue)")
     ($ react/Suspense #js {:fallback ($ "div" nil "Fetching pokemeon...")}
        ($ PokemonDetailStoreDeferred))
     ($ "hr")
     ($ "h3" nil "Pokemon Demo (useStore + useTransition)")
     ($ react/Suspense #js {:fallback ($ "div" nil "Fetching pokemeon...")}
        ($ PokemonDetailStore))))