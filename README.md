# Reseda

A set of libraries and ideas to build ClojureScript applications based on modern React.

## Rationale

For a long time, React applications in ClojureScript would rely on comprehensive libraries such as [Om](https://github.com/omcljs/om), [Reagent](https://github.com/reagent-project/reagent) and [re-frame](https://github.com/day8/re-frame), [Fulcro](https://fulcro.fulcrologic.com), etc. for a big chunk of web-applications concerns and treat React as a pure view library. In particular, on top of state management, these libraries would also handle the reactivity part of React: making sure components re-render when a piece of state changes.

With the introduction of [Context](https://reactjs.org/docs/context.html), [Hooks](https://reactjs.org/docs/hooks-intro.html), and the (currently work-in-progress) [Concurrent Mode](https://reactjs.org/docs/concurrent-mode-intro.html), React is getting more and more opinionated about state management, while at the same time exposing lower-level primitives that can allow finer-grained control over reactivity.

Reseda explores the space of using Clojure's philosophy of [Identity, State, and Values](https://www.infoq.com/presentations/Value-Identity-State-Rich-Hickey/) for state management, while leaning on React for reactivity. The result is a set of small composable namespaces that work with plain React components, which can be combined and extended to build richer abstractions according to each application needs.

## Status

Still in exploration, pre-alpha.

Happily accepting issues to have discussions on related topics.

## Vision and Goals

At this early stage, Reseda aims to explore the various domains as a small, decomposed problems. Examples include:

* State & Reactivity
* Changing state
* Event-based architecture
* Side-effects (IO/timeouts/etc)
* Routing, code-splitting, [Suspense](https://reactjs.org/docs/react-api.html#suspense) & [Render-as-you-fetch](https://reactjs.org/docs/concurrent-mode-suspense.html#approach-3-render-as-you-fetch-using-suspense)

With the goal being to provide one library per problem. The libraries should be composable with each other to build bespoke frameworks (a la carte). Perhaps a natural framework will also arise from all this work, perhaps not.