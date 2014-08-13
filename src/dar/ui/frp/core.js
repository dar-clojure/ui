goog.provide('dar.ui.frp.core')

;(function(exports) {

var uidCounter = 0

function uid() {
  return uidCounter++
}

function extend(Klass, Proto) {
  Klass.prototype = Object.create(Proto.prototype)
}

function App() {
  this.signals = {}
  this.queue = new Heap
  this.topPriority = Number.MIN_VALUE
  this.events = []
}

App.prototype.get = function(signal) {
  var s = this.signals[signal.uid]
  return s && s.value
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

function Signal() {
  this.uid = uid()
}

Signal.prototype.createState = function(app) {
  return new State(this.uid, app)
}

function State(uid, app) {
  this.uid = uid
  this.app = app
  this.event = false
  this.priority = 0
  this.listeners = {}
  this.value = null
  this.dirty = false
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

State.prototype.markListenersDirty = function() {
  var app = this.app
  for(var key in this.listeners) {
    app.markDirty(app.signals[keys])
  }
}

function Transform(fn, inputs) {
  this.uid = uid()
  this.fn = fn
  this.inputs = inputs
}

Transform.prototype.createState = function(app) {
  return new ATransform(this.uid, app, this.fn, this.inputs)
}

function ATransform(uid, app, fn, inputs) {
  State.call(this, uid, app)
  this.fn = fn
  this.setInputs(inputs)
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

function Switch(input) {
  this.uid = uid()
  this.input = input
}

Switch.prototype.createState = function(app) {
  return new ASwitch(this.uid, app, this.input)
}

function ASwitch(uid, app, input) {
  State.call(this, uid, app)
  this.input = app.state(input)
}

extend(ASwitch, State)

ASwitch.prototype.recompute = function() {
  var signal = this.input.value
  if (!signal) return this.value = null
  var s = this.app.state(signal)
  if (this.app.requeue(this, s.priority)) return
  this.value = s.value
}

exports.App = App
exports.Signal = Signal
exports.Transform = Transform
exports.Switch = Switch

function Heap(compare){
  this.compare = compare || function(a, b){ return a - b }
  this.arr = []
}

Heap.prototype.peek = function(){
  return this.arr[0]
}

Heap.prototype.push = function(v){
  var a = this.arr
    , compare = this.compare
    , pos = a.length
    , parent
    , x

  a.push(v)

  while(pos > 0) {
    parent = (pos - 1) >>> 1
    if (compare(a[pos], a[parent]) >= 0) return
    x = a[parent]
    a[parent] = a[pos]
    a[pos] = x
    pos = parent
  }
}

Heap.prototype.pop = function(){
  var a = this.arr
    , top = a[0]
    , last = a.pop()

  if (a.length > 0) {
    a[0] = last

    var pos = 0
      , lastIdx = a.length - 1
      , compare = this.compare
      , left
      , right
      , min
      , x

    while(1) {
      left = (pos << 1) + 1
      right = left + 1
      min = pos
      if (left <= last && compare(a[left], a[min]) < 0) min = left
      if (right <= last && compare(a[right], a[min]) < 0) min = right
      if (min == pos) break
      x = a[min]
      a[min] = a[pos]
      a[pos] = x
      pos = min
    }
  }

  return top
}

Heap.prototype.resort = function() {
}

Heap.prototype.init = function(arr) {
  this.arr = arr
  this.resort()
}

})(dar.ui.frp.core)