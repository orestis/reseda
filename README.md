# Reseda

![tests](https://github.com/orestis/reseda/workflows/tests/badge.svg)

A Clojure-y state management library for modern React, from the future 🚀

## Rationale

For a long time, React applications in ClojureScript would rely on comprehensive libraries such as [Om](https://github.com/omcljs/om), [Reagent](https://github.com/reagent-project/reagent) and [re-frame](https://github.com/day8/re-frame), [Fulcro](https://fulcro.fulcrologic.com), etc. for a big chunk of web-application concerns, while treating React as purely a view layer. In particular, on top of state management, these libraries would also deal with reactivity: making sure components re-render when a piece of state changes.

With the introduction of [Context](https://reactjs.org/docs/context.html), [Hooks](https://reactjs.org/docs/hooks-intro.html), and the (currently work-in-progress) [Concurrent Mode](https://reactjs.org/docs/concurrent-mode-intro.html), React is getting more and more opinionated about state management, while at the same time exposing lower-level primitives that can allow fine-grained control over reactivity.

Reseda explores the space of using Clojure's philosophy of [Identity, State, and Values](https://www.infoq.com/presentations/Value-Identity-State-Rich-Hickey/) for state management, while leaning on React for reactivity. 

The result is a state managment library that works with plain React components, is REPL friendly, uses plain Clojure atoms as the underlying storage mechanism, can be used both for global and local state, and can be used whenever `useState` is inadequate. 

In addition, by fully embracing [Suspense for Data Fetching](https://reactjs.org/docs/concurrent-mode-suspense.html), Reseda allows you to build User Interfaces without asynchronous data fetching concerns, resulting in less moving parts in your application. And by being compatible with React Stable, it gives you a glimpse of the future today 😎

## Status

Used in production at [Nosco](https://nos.co), and evolves as we explore various use cases.

The API might change (but probably not), and there's some unit test coverage but still – use at your own risk.

Happily accepting issues to discuss use cases, bugs, new features etc.

See [Vision](./VISION.md) for the original vision and potential future additions.

## Usage

Use `deps.edn` to get a reference to the latest commit, e.g.:

    {orestis/reseda {:git/url "https://github.com/orestis/reseda.git"
                     :sha "<latest commit>"}}

## Getting Started

### Setting up the store

At the core, Reseda exposes a `Store` that wraps an `IWatchable` (most often a Clojure atom).

```clojure
(:require [reseda.state])

;; create the atom
(defonce backing-store (atom {}))

;; create a new store
(defonce store (reseda.state/new-store backing-store))
```

Having the store at hand, you can subscribe to be notified whenever something in the backing store changes:

```clojure
;; whenever the value under :fred changes, print it
(subscribe store :fred println)

;; you can pass in any function as the selector, not just keywords:
(subscribe store identity tap>)
(subscribe store (fn [m] (select-keys m [:fred :wilma])) tap>)

;; as a convenience, you can also pass a vector of keywords as the selector, like get-in
(subscribe store [:fred :name] #(log %))
```

Note that the value you get is whatever the selector returns, and Clojure's equality via `=` is used to determine if a change was made. The `on-change` function receives a single argument, the new value.

Obviously, you can put whatever you want inside the backing store - most usually it will be a map, but it might be a vector or anything else that you can put in an atom. 

You can implement the `IWatchable` protocl (CLJS) or `clojure.lang.IRef` interface (Clojure) for something fancier -- e.g. to trigger subscriptions based on a websocket connection etc.

### React integration

So far the code is cross-platform, but the main use of Reseda is for building UIs with React.

The basic setup is exactly the same:

```clojure
(ns reseda.readme 
  (:require [reseda.state]
            [reseda.react]
            [hx.react :refer [defnc]]))

;; be sure to use defonce or your state will be gone if you use hot-reloading on save
(defonce backing-store (atom {:user {:name "Fred"
                                     :email "fred@example.com"}}))
(defonce store (reseda.state/new-store backing-store))
```

You can then re-render a component whenever something changes in the store via the `useStore` hook:

```clojure
;; The first render will give you whatever is in the store, and
;; from then on, your component will re-render whenever the value changes
(defnc Name []
  (let [name (reseda.react/useStore store [:user :name])]
   [:div "The user's name is: " name]))
```

Note that [hx](https://github.com/lilactown/hx) is used for the examples, but any library that can
make use of React Hooks can be used.

To make changes, simply change the underlying backing store however you see fit, e.g.:

```clojure
(defnc EditName []
  (let [name (reseda.react/useStore store [:user :name])]
    [:input {:value name
             :on-change #(swap! backing-store assoc-in 
                                              [:user :name]
                                              (-> % .-target .-value))}]))
```

You have the entire Clojure toolbox at your disposal to make changes. Use plain maps, a statechart library, a Datascript database, whatever fits your use case.


### Suspense Integration (Stable)

Reseda supports [Suspense for Data Fetching](https://reactjs.org/docs/concurrent-mode-suspense.html) even in React Stable (16.13), even though it's technically not supported. 

This allows you to avoid a whole bunch of asynchronous code by allowing React to suspend rendering if some remote value hasn't arrived yet.

At the core of this support is the `Suspending` type, which you can construct by giving it a Promise:

```clojure
(ns reseda.readme
 (:require [reseda.state]
           [reseda.react]
           ["react" :as React]))

(defn fetch-api []
 ;; fetch a remote resource and return a Javascript Promise
 ,,,)

(defonce backing-store (atom {:data (-> (fetch-api)
                                        (reseda.react/suspending-value))}))
(defonce store (reseda.state/new-store backing-store))

;; :data now contains a Suspending that wraps the Promise
(realized? (:data @backing-store))
;;=> false

;; after some time passes, the Promise resolves:
(realized? (:data @backing-store))
;;=> true

;; you can now deref the Suspending to get the actual data:
(deref (:data @backing-store))
;;=> <the remote data>
```

The magic happens you combine a Suspending with a React Suspense Boundary:

```clojure

(defnc RemoteName []
 ;; using a trailing * for reader clarity -- 
 ;; this is a Suspending and you need to deref it
 (let [data* (reseda.react/useStore store :data)]
   ;; notice the @ that derefs the Suspending
   [:div "The remote data is: " @data*]))

(defnc Root []
 ;; see note about Suspense boundaries 
 ;; -- you cannot have them in the same component that suspends
 [React/Suspense {:fallback (hx/f [:div "Loading..."])}
  [RemoteName]])
```

When React tries to render `RemoteName`, and the data hasn't fetched yet, the `deref` of the Suspending will cause React to "suspend". This means that the closest `Suspense` component will show its fallback element instead of its children. When the promise resolves, React will try to re-render, in which case the data will have been loaded and rendering proceeds normally (or until a child component suspends).

Read more about [Suspense in the official React Docs](https://reactjs.org/docs/react-api.html#reactsuspense).

#### Additional utilities

The final trick that Reseda provides (hoping that it won't be used once Suspense for data loading is stable), is the ability to show *previous versions* of a Suspending value.

You want to do this when showing the fallback element will provide a jarring user experience. For example, if you've loaded a results list, and you want to update the list as the user sorts or narrows down the search:

```clojure
(defnc SearchResults [{:keys [results*]}]
 [:div (for [v @results*])
         [Row {:value v}]])

(defnc SearchList []
 (let [results* (reseda.react/useStore store :results)]
   [SearchBox {:on-change
               (fn [text]
                 (swap! backing-store :results (fetch-new-results text)))}]
   [React/Suspense
    [SearchResults {:results* results*}]]))
```

The moment you change the `:results` value of the backing store to a new Suspending, Reseda will make your component re-render, which in turn will make React suspend, meaning the previous results will be gone from the screen. Not cool.

To avoid this, wrap the `Suspending` value with a `useSuspending` hook like so:

```clojure
(defnc SearchList []
 (let [[results* loading?] (-> (reseda.react/useStore store :results) 
                               (reseda.react/useSuspending))]
   [SearchBox {:show-spinner loading?
               :on-change 
               (fn [text]
                (swap! backing-store :results (fetch-new-results text)))}]
   [React/Suspense
    [SearchResults {:results* results*
                    :loading? loading?}]]))
```

`useSuspending` will connect the passed-in `Suspending` with the lifecycle of the React component, and will always return
the value that was realized *last* (or the only one, during the first render). It will also return a boolean that indicates that data is on the way and can be used to indicate loading states in the UI.

Note: `useSuspending` will add a callback to the underlying Promise of the `Suspending`. This should be harmless and only does side-effects related to React. The actual value is passed-through unchanged.

This behaviour is relatively similar to the [`useTransition` hook](https://reactjs.org/docs/concurrent-mode-reference.html#usetransition) in React experimental, but less granular and without timeout support.

### Suspense Integration (Experimental)

It's been years since Concurrent Mode has been announced, and it's still experimental. However, if you're keen on using it, you can use the `reseda.react.experimental` namespace like so:

```clojure
(ns reseda.readme
 (:require [reseda.state]
           [reseda.react]
           [reseda.react.experimental]
           [hx.react :refer [defnc]]
           ["react" :as React]))

(defonce backing-store (atom {}))

;; note the new name, this is not used directly
(defonce reseda-store (reseda.state/new-store backing-store))

;; you need to wrap the basic store with a version that's Concurrent-mode aware
(defonce store (reseda.react.experimental/wrap-store reseda-store))

(defnc Name []
  ;; different flavor of useStore
  (let [name (reseda.react.experimental/useStore store [:user :name])]
   [:div "The user's name is: " name]))
```

Behind the scenes this is using `useMutableSource` so it's much less code than the React Stable version, and as Concurrent Mode safe as you can be with global state.

### Local state

While all the examples so far were dealing with global atoms and stores, you can also use Reseda for local state. You just need to make sure that React doesn't throw away your local state. You can do that with a `useRef`:

```clojure
(defnc ComplexComponent []
 (let [backing-ref (React/useRef (atom {})
       backing (.-current backing-ref) 
       store-ref (React/useRef (reseda.state/new-store backing)))
       store (.-current store-ref)]
    [ReadOnlyComponent {:store store}
    [WriteOnlyComponent {:backing backing}
    [ReadWriteComponent {:store store :backing backing}]]]))
```

React will make sure that the atom and the wrapping store will stay the same during the lifecycle of the component (ie from mount to unmount), so you can pass the "current" value around as props to any child components that may need them.

The separation of store and backing also makes it clear if a component is just reading values from the store or also writing values into it.

### Gotchas and advanced topics

Due to the way React works, you need to keep in mind a few things:

#### Selector functions 

Selector functions should have a consistent identity, (i.e. not re-created every render), otherwise you may go into a render-loop.

* Keywords and vectors of keywords "just work"
* Functions defined via `defn` also work, since their identity doesn't change
* Anything else has to be wrapped inside a `useCallback`


Reseda doesn't try to do any batching or asynchronicity of subscriptions. This means that one change to the underlying atomwill trigger one subscription (assuming of course the selected value *does* change).

This is fine in practice, since often React will batch the DOM updates, but if you care to avoid multiple renders, make sure you `swap!` just once.

#### Caching, derivative values and extra logic

Reseda doesn't do any caching and will naively re-run all your subscriptions every time the underlying atom changes.

If some selector functions are expensive, you would probably want to either pre-calculate their values and store them in a different place. You can do this in numerous ways, e.g.:

* By doing all the work during a simple `swap!`
* By adding an extra watch (via plain `add-watch`) *before* you create the Reseda `Store`. This way you can catch changes to the store before the Reseda subscriptions run.

#### Suspense Boundaries

You cannot have a Suspense boundary inside the component that does the Suspending. This is because React walks the component tree upwards to find the next Suspense boundary, and will throw away the results of the current render (that put the inline Suspense boundary in place).



## Non-goals

* Creating React elements via hiccup or other means. There is already a lot of exploration happening in the space, with libraries such as [hx](https://github.com/Lokeh/hx) and [helix](https://github.com/Lokeh/helix), and Hiccup parsers such as [Hicada](https://github.com/rauhs/hicada) and [Sablono](https://github.com/r0man/sablono).

* Server-side rendering (Node or JVM). I'm not personally interested in this for the kind of applications I develop. However once the progressive hydration story of React stabilises, it might be interesting to revisit.

## Demo

There's various demos at [src/reseda/demo](src/reseda/demo). You can follow along at https://reseda.orestis.gr or run `clojure -A:demo:shadow-cljs watch demo`

## License

```
Copyright (c) Orestis Markou. All rights reserved. The use and
distribution terms for this software are covered by the Eclipse
Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
which can be found in the file epl-v10.html at the root of this
distribution. By using this software in any fashion, you are
agreeing to be bound by the terms of this license. You must
not remove this notice, or any other, from this software.
```
