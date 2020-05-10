# State & Reactivity

Clojure already provides a perfectly good approach to state: immutable data structures inside of mutable containers, i.e. atoms. Atoms already provide watches, so observing changes of atoms is built-in. Therefore it makes sense to use plain Clojure atoms as the "backing store" of all state in a web application.

On top of this backing store, with some simple React Hooks, components can selectively re-render when the part of the state they are interested in has changed. This is an abstraction that encapsulates an atom and deals with subscriptions.

## REPL and development experience

It's a goal to be able to use the REPL to inspect and manipulate the state. This is reflected by exposing the backing store atom directly, and by having protocol methods that can inspect and manipulate the state of the store.

## Subscriptions & Selectors

We don't want to re-render the whole component tree when the root atom changes, so components should be able to subscribe to only part of the state, using a "selector" that takes the whole state and returns the interesting part. For maximum flexibility, selectors are just functions. Supporting path-based selectors (for `get-in` like usage) or even more complex selectors ([Specter](https://github.com/redplanetlabs/specter), [Meander](https://github.com/noprompt/meander)) can be handled at the next abstraction level or even the application level.

## Updating state (also: cursors?)

Updating the state is done using also the atom interface (`swap!`, `reset!`) etc. Coupling the selectors to updates (a-la cursors) is unnecessary and can be done at another level of abstraction or at the application level.

## Multiple atoms

While putting all application state in a single atom makes sense from a low complexity point of view, there's no need to enforce that. Having the option to create and dispose many different atoms allows for more flexibility, and moves the decision to application authors.

## References to state

State can either be global (e.g. a `defonce`d var) or can be component-local (using perhaps [`useRef`](https://reactjs.org/docs/hooks-reference.html#useref)) and could also be passed via [`Context`](https://reactjs.org/docs/context.html).

## Performance

There might be a need to cache previous results, de-duplicate selectors, and other performance optimisations. Before going there, adding some performance metrics to measure how long do state updates take and exposing that to the application is a good starting point.
