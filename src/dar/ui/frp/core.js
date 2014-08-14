goog.provide('dar.ui.frp.core')

;(function(exports) {

var uidCounter = 0

function uid() {
  return uidCounter++
}

function extend(Klass, Proto) {
  Klass.prototype = Object.create(Proto.prototype)
}

function comparePriorities(a, b) {
  return b.priority - a.priority
}

exports.App = App

function App() {
  this.signals = {}
  this.queue = new Heap(comparePriorities)
  this.topPriority = Number.MIN_VALUE
  this.events = []
}

App.prototype.get = function(signal) {
  var s = this.signals[signal.uid]
  return s && s.value
}

App.prototype.watch = function(signal, cb) {
  var watch = new Transform(function(prev, args) {
    cb(prev, args[0])
  }, [signal])
  this.state(watch)
  return watch
}

App.prototype.push = function(signal, v) {
  var s = this.signals[signal.uid]
  if (!s) return
  if (s.value === v) return
  s.value = v
  s.markListenersDirty()
  this.recompute()
}

App.prototype.markDirty = function(s) {
  if (s.dirty) return
  s.dirty = true
  this.queue.push(s)
}

App.prototype.state = function(signal) {
  var s = this.signals[signal.uid]
  if (!s) {
    s = signal.createState(this)
    this.signals[signal.uid] = s
    if (this.topPriority > s.priority) {
      this.markDirty(s)
    } else {
      this.recomputeState(s)
    }
  }
  return s
}

App.prototype.recompute = function() {
  var s
  while(s = this.queue.pop()) {
    if (s.killed) continue
    this.topPriority = s.priority
    this.recomputeState(s)
  }
  this.topPriority = Number.MIN_VALUE
  this.clearEvents()
}

App.prototype.recomputeState = function(s) {
  var prev = s.value
  s.dirty = false
  s.recompute()
  if (!s.dirty && prev !== s.value) {
    s.markListenersDirty()
    if (s.event) this.events.push(s)
  }
}

App.prototype.clearEvents = function() {
  for(var i = 0; i < this.events.length; i++) {
    this.events[i].value = null
  }
  this.events = []
}

App.prototype.requeue = function(s, priority) {
  if (s.priority < priority) return false
  var needQueueShuffle = this.lowerPriority(s, priority)
  if (needQueueShuffle) this.queue.resort()
  this.markDirty(s)
  return true
}

App.prototype.lowerPriority = function(s, priority) {
  if (priority > s.priority) return false
  s.priority = priority - 1
  var touchedDirty = s.dirty
  for(var key in s.listeners) {
    var touched = this.lowerPriority(this.signals[key], s.priority)
    touchedDirty = touchedDirty || touched
  }
  return touchedDirty
}

App.prototype.kill = function(s, listener) {
  if (listener) s.removeListener(listener)
  if (s.hasListeners()) return
  delete this.signals[s.uid]
  s.killed = true
  s.onkill()
}

exports.Signal = Signal

function Signal(val) {
  this.uid = uid()
  this.value = val
  this.event = false
}

Signal.prototype.createState = function(app) {
  return new State(this, app)
}

Signal.prototype.asEvent = function() {
  this.event = true
  return this
}

function State(spec, app) {
  this.uid = spec.uid
  this.value = spec.value
  this.event = spec.event
  this.app = app
  this.priority = 0
  this.listeners = {}
  this.dirty = false
  this.killed = false
}

State.prototype.addListener = function(s) {
  this.listeners[s.uid] = true
}

State.prototype.removeListener = function(s) {
  delete this.listeners[s.uid]
}

State.prototype.hasListeners = function() {
  for(var key in this.listeners) {
    return true
  }
  return false
}

State.prototype.onkill = function() {}

State.prototype.markListenersDirty = function() {
  var app = this.app
  for(var key in this.listeners) {
    app.markDirty(app.signals[keys])
  }
}

exports.Transform = Transform

function Transform(fn, inputs) {
  this.uid = uid()
  this.fn = fn
  this.inputs = inputs
  this.event = false
}

Transform.prototype.asEvent = function() {
  this.event = true
  return this
}

Transform.prototype.createState = function(app) {
  return new ATransform(this, app)
}

function ATransform(spec, app {
  State.call(this, spec, app)
  this.fn = spec.fn
  this.setInputs(spec.inputs)
}

extend(ATransform, State)

ATransform.prototype.setInputs = function(inputs) {
  this.inputs = new Array(inputs.length)
  for(var i = 0; i < inputs.length; i++) {
    var input = this.inputs[i] = this.app.state(inputs[i])
    this.priority = Math.min(this.priority, input.priority - 1)
    input.addListener(this)
  }
}

ATransform.prototype.recompute = function() {
  var args = new Array(this.inputs.length)
  for(var i = 0; i < this.inputs.length; i++) {
    args[i] = this.inputs[i].value
  }
  this.value = this.fn(this.value, args)
}

ATransform.prototype.onkill = function() {
  for(var i = 0; i < this.inputs.length; i++) {
    this.app.kill(this.inputs[i], this)
  }
}

exports.mergeTransform = function(_, vals) {
  for(var i = 0; i < vals.length; i++) {
    if (vals[i] != null) return vals[i]
  }
}

exports.Switch = Switch

function Switch(input) {
  this.uid = uid()
  this.input = input
  this.event = false
}

Switch.prototype.asEvent = function() {
  this.event = true
  return this
}

Switch.prototype.createState = function(app) {
  return new ASwitch(this, app)
}

function ASwitch(spec, app) {
  State.call(this, spec, app)
  this.input = app.state(spec.input)
  this.priority = this.input.priority - 1
}

extend(ASwitch, State)

ASwitch.prototype.recompute = function() {
  var signal = this.input.value
    , s = signal && this.app.state(signal)

  if (signal !== this.signal) {
    if (this.signal) this.app.kill(this.signal, this)
    this.signal = signal
    if (s) {
      s.addListener(this)
      if (this.app.requeue(this, s.priority)) return
    }
  }

  this.value = s && s.value
}

ASwitch.prototype.onkill = function() {
  var s = this.signal && this.app.state(this.signal)
  if (s) this.app.kill(s, this)
  this.app.kill(this.input, this)
}

exports.Heap = Heap

function Heap(compare){
  this.compare = compare
  this.arr = []
}

Heap.prototype.peek = function(){
  return this.arr[0]
}

Heap.prototype.size = function() {
  return this.arr.length
}

Heap.prototype.push = function(v){
  var a = this.arr
    , compare = this.compare
    , pos = a.length
    , parent

  a.push(v)

  while(pos > 0) {
    parent = (pos - 1) >>> 1
    if (compare(a[parent], v) < 0) break
    a[pos] = a[parent]
    pos = parent
  }

  a[pos] = v
}

Heap.prototype.pop = function(){
  var a = this.arr
    , top = a[0]
    , poped = a.pop()

  if (a.length > 0) {
    siftDown(a, poped, 0, a.length - 1, this.compare)
  }

  return top
}

function siftDown(a, v, pos, last, compare) {
  var left
    , right
    , next

  while(true) {
    left = (pos << 1) + 1
    right = left + 1

    if (right <= last) {
      next = compare(a[right], a[left]) < 0
        ? compare(a[right], v) < 0 ? right : pos
        : compare(a[left], v) < 0 ? left : pos
    } else if (left == last) {
      next = compare(a[left], v) < 0 ? left : pos
    } else {
      next = pos
    }

    if (next == pos) break
    a[pos] = a[next]
    pos = next
  }

  a[pos] = v
}

Heap.prototype.resort = function() {
  var a = this.arr
    , compare = this.compare
    , last = a.length - 1
    , i = (last - 1) >>> 1

  if (a.length == 0) return

  while(i >= 0) {
    siftDown(a, a[i], i, last, compare)
    i--
  }
}

Heap.prototype.init = function(arr) {
  this.arr = arr
  this.resort()
}

})(dar.ui.frp.core)