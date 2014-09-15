#dar.ui

Frontend web framework that aims to make development
of rich, highly interactive user interfaces as easy as
plain HTML document rendering. It achieves that
by employing simple and efficient virtual DOM abstraction,
as well as empowering conventional [FRP](http://en.wikipedia.org/wiki/Functional_reactive_programming)
approuch to bring long missing idioms proved to be useful and necessary for
one-shot data transformations.

##Overview

###DOM

Virtual DOM tree is entierly immutable, no part is statefull, there are no exceptions.
Updates are always proceed from the top root element by parallel depth-first walk
over real DOM and two versions of virtual tree.

The key insight of fast updates is that, because our tree is entirely immutable, we
can quickly check two nodes for been identical, and if so, just skip the entire branch.
Another point is that, FRP system helps us to avoid rebuilding the whole tree
each time, giving both modularity and performance.

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
jquery plugin" transformations, except this time it feels like
a "perfectly valid technique", not like a hack.

###FRP

It is an efficient push based event system.
It touches only necessary parts of the signal graph during update.

Signals are first class.

Signals are immutable objects and completely stateless. They have
just an id and some metadata.

Actual state is instantiated on demand on an `app` instance.
During runtime it is possible to have several apps running
the same signal graph.

Signal graph is dynamic, i.e. it is possible to plug-detach new signals
conditionally, depending on the current state.

The above fact together with some other tricks is the main source of power.
It allows to create components with local state, while still retaining
all benefits of pure FRP system.

##Installation

Not yet released.

##License

Copyright © 2014 Eldar Gabdullin

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
