#ui

Frontend web framework that aims to make development
of rich, highly interactive user interfaces as easy as
plain HTML document rendering.

It is based on [Functional Reactive Programming](http://en.wikipedia.org/wiki/Functional_reactive_programming)
and very best ideas from [React/Om](http://swannodette.github.io/2013/12/17/the-future-of-javascript-mvcs/).

##Overview

###Virtual DOM

Virtual DOM tree is entierly immutable, no part is statefull, there are no exceptions.
Updates always proceed from top by parallel depth-first walk over new version, old version and
real DOM.

The key insight of fast updates is that, because our tree is entirely immutable, we
can quickly check two nodes for been identical, and if so, just skip the entire branch.
Another point is that, FRP system helps us to avoid rebuilding the whole tree
each time.

In practice that means that if, for example, our app consists of header, footer and body,
we'll have three signals (for header, footer and body) and some "assembling" function
to produce the root element.

```Clojure
(defn assemble-dom [header footer body]
  (DIV {:id "app"}
    header
    body
    footer))
```

Then we can lift `assemble-dom` to take signals.

It's worth noting, that (unlike with React) it is easy to perform various "a-la
jquery plugin" transformations.

###FRP

It is a push based event system.

It touches only necessary parts of the signal graph during update
and only once.

Signals are first class.

Signals are immutable objects and completely stateless. They have
just an id and some metadata.

Actual state is instantiated on demand on an `app` instance.
During runtime it is possible to have several apps running
the same signal graph.

Signal graph is dynamic, i.e. it is possible to plug-detach-create-link signals
conditionally, depending on the current state.

##Installation

Not yet released.

##License

Copyright © 2014 Eldar Gabdullin

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
