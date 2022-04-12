* Fix the failing React 18 tests
* Address the issue of "initial value"

When we first render, ideally the store would be prepopulated
by the parent component.
But often times, that's not practical, so we rely on a useEffect.
Which means that exists a brief time that the store of the value is nil
It used to be that we can't suspend with a promise that never resolves
(React 16.8 was hanging for some reason).
Revisit if we can do that today with react 17 or 18.