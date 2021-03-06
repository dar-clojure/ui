goog.provide('dar.ui.frp.core')

;(function(exports) {

var uidCounter = 0

function newUid() {
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
  this.events = []
}

App.prototype.get = function(signal) {
  var s = this.signals[signal.uid]
  return s && s.value
}

App.prototype.watch = function(signal, cb) {
  var watch = new Transform(function(prev, args) {
    cb(args[0], prev)
    return args[0]
  }, [signal])
  this.state(watch)
  this.recompute()
  return watch
}

App.prototype.push = function(signal, v) {
  var s = this.signals[signal.uid]
  if (!s) return
  if (s.value === v) return
  if (s.event) this.events.push(s)
  s.value = v
  s.markListenersDirty()
}

App.prototype.state = function(signal) {
  var s = this.signals[signal.uid]
  if (!s) {
    s = this.signals[signal.uid] = signal.createState(this)
    s.init()
    s.markDirty()
  }
  return s
}

App.prototype.recompute = function() {
  var s
  while(s = this.queue.pop()) {
    if (s.killed) continue
    this.recomputeState(s)
  }
  this.clearEvents()
}

App.prototype.recomputeState = function(s) {
  var prev = s.value
  s.dirty = false
  s.recompute()
  if (s.dirty || prev === s.value) return
  s.markListenersDirty()
  if (s.event) this.events.push(s)
}

App.prototype.clearEvents = function() {
  for(var i = 0; i < this.events.length; i++) {
    this.events[i].value = null
  }
  this.events = []
}

exports.Signal = Signal

function Signal(val) {
  this.uid = newUid()
  this.value = val
  this.event = false
}

Signal.prototype.createState = function(app) {
  return new State(this, app)
}

function State(spec, app) {
  this.uid = spec.uid
  this.value = spec.value
  this.event = spec.event
  this.app = app
  this.spec = spec
  this.priority = 0
  this.listeners = {}
  this.dirty = false
  this.killed = false
}

State.prototype.addListener = function(s) {
  this.listeners[s.uid] = s
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

State.prototype.init = function() {}

State.prototype.onkill = function() {}

State.prototype.recompute = function() {}

State.prototype.markDirty = function() {
  if (this.dirty) return
  this.dirty = true
  this.app.queue.push(this)
}

State.prototype.markListenersDirty = function() {
  for(var key in this.listeners) {
    this.listeners[key].markDirty()
  }
}

State.prototype.dependOn = function(signal) {
  var s = this.app.state(signal)
  s.addListener(this)
  this.priority = Math.min(this.priority, s.getDownstreamPriority())
  return s
}

State.prototype.getDownstreamPriority = function() {
  return this.priority - 1
}

State.prototype.lowerListenersPriority = function(priority) {
  var ret = false
  for(var key in this.listeners) {
    var touchedDirty = this.listeners[key].lowerPriority(priority)
    ret = ret || touchedDirty
  }
  return ret
}

State.prototype.lowerPriority = function(priority) {
  if (this.priority <= priority) return false
  if (this.lowering) throw new Error('Cycle in the signal graph')
  this.lowering = true
  this.priority = priority
  var touchedDirty = this.lowerListenersPriority(priority - 1)
  this.lowering = false
  return this.dirty || touchedDirty
}

State.prototype.kill = function(listener) {
  if (listener) this.removeListener(listener)
  if (this.hasListeners()) return
  delete this.app.signals[this.uid]
  this.killed = true
  this.onkill()
}

exports.Transform = Transform

function Transform(fn, inputs) {
  this.uid = newUid()
  this.fn = fn
  this.inputs = inputs
  this.event = false
}

Transform.prototype.createState = function(app) {
  return new ATransform(this, app)
}

function ATransform(spec, app) {
  State.call(this, spec, app)
}

extend(ATransform, State)

ATransform.prototype.init = function() {
  var inputs = this.spec.inputs
  this.inputs = new Array(inputs.length)
  for(var i = 0; i < inputs.length; i++) {
    this.inputs[i] = this.dependOn(inputs[i])
  }
}

ATransform.prototype.recompute = function() {
  var args = new Array(this.inputs.length)
  for(var i = 0; i < this.inputs.length; i++) {
    args[i] = this.inputs[i].value
  }
  this.value = this.spec.fn(this.value, args)
}

ATransform.prototype.onkill = function() {
  for(var i = 0; i < this.inputs.length; i++) {
    this.inputs[i].kill(this)
  }
}

exports.mergeTransform = function(_, vals) {
  for(var i = 0; i < vals.length; i++) {
    if (vals[i] != null) return vals[i]
  }
}

exports.Switch = Switch

function Switch(input) {
  this.uid = newUid()
  this.input = input
  this.event = false
}

Switch.prototype.createState = function(app) {
  return new ASwitch(this, app)
}

function ASwitch(spec, app) {
  State.call(this, spec, app)
}

extend(ASwitch, State)

ASwitch.prototype.init = function() {
  this.input = this.dependOn(this.spec.input)
  this.downstreamPriority = this.priority - 1
}

ASwitch.prototype.recompute = function() {
  var signal = this.input.value
    , old = this.signal
    , oldState = this.signalState

  if (signal !== old) {
    this.plugNewSignal(signal)
    if (oldState) oldState.kill(this)
  }

  if (signal) {
    this.dummy = new DummySwitch(this)
    this.dummy.markDirty()
  }
}

ASwitch.prototype.plugNewSignal = function(signal) {
  if (!signal) return this.signal = this.signalState = this.value = null
  this.signal = signal
  var s = this.signalState = this.app.state(signal)
  s.addListener(this)
  var dp = Math.min(this.priority - 1, s.getDownstreamPriority())
  if (this.lowerDownstream(dp)) this.app.queue.resort()
}

ASwitch.prototype.lowerPriority = function(priority) {
  if (this.priority < this.input.getDownstreamPriority())
    return this.lowerDownstream(priority)
  this.priority = priority
  var dirty = this.lowerDownstream(priority - 1)
  return this.dirty || dirty
}

ASwitch.prototype.lowerDownstream = function(priority) {
  if (this.downstreamPriority <= priority) return false
  this.downstreamPriority = priority
  var touchedDirty = this.lowerListenersPriority(priority - 1)
  if (this.dummy) this.dummy.priority = priority
  return !!this.dummy || touchedDirty
}

ASwitch.prototype.getDownstreamPriority = function() {
  return this.downstreamPriority - 1
}

ASwitch.prototype.onkill = function() {
  if (this.dummy) this.dummy.killed = true
  if (this.signalState) this.signalState.kill(this)
  this.input.kill(this)
}

function DummySwitch(sw, delayed) {
  this.app = sw.app
  this.priority = sw.downstreamPriority
  this.value = sw.value
  this.sw = sw
  this.delayed = delayed
}

extend(DummySwitch, State)

DummySwitch.prototype.recompute = function() {
  this.sw.dummy = null
  this.value = this.sw.value = this.sw.signalState.value
}

DummySwitch.prototype.markListenersDirty = function() {
  if (this.delayed) return
  for(var key in this.sw.listeners) {
    this.sw.listeners[key].markDirty()
  }
}

exports.DSwitch = DSwitch

function DSwitch(input) {
  this.uid = newUid()
  this.input = input
  this.event = false
}

DSwitch.prototype.createState = function(app) {
  return new ADSwitch(this, app)
}

function ADSwitch(spec, app) {
  State.call(this, spec, app)
}

extend(ADSwitch, ASwitch)

ADSwitch.prototype.recompute = function() {
  var signal = this.input.value
    , old = this.signal
    , oldState = this.signalState

  if (signal !== old) {
    this.plugNewSignal(signal)
    if (oldState) oldState.kill(this)
  }

  if (signal) {
    this.dummy = new DummySwitch(this, signal !== old)
    this.dummy.markDirty()
  }
}

exports.SignalsMap = SignalsMap

function SignalsMap(input, sf) {
  this.uid = newUid()
  this.input = input
  this.sf = sf
}

SignalsMap.prototype.createState = function(app) {
  return new ASignalsMap(this, app)
}

exports.ASignalsMap = ASignalsMap

function ASignalsMap(spec, app) {
  State.call(this, spec, app)
}

extend(ASignalsMap, State)

ASignalsMap.prototype.init = function() {
  this.input = this.dependOn(this.spec.input)
}

ASignalsMap.prototype.recompute = function() {
  throw new Error('This should be implemented in Clojure')
}

ASignalsMap.prototype.onkill = function() {
  this.app.kill(this.input, this)
}

exports.Port = Port

function Port(fn) {
  this.uid = newUid()
  this.fn = fn
  this.event = false
}

Port.prototype.createState = function(app) {
  return new APort(this, app)
}

function APort(spec, app) {
  State.call(this, spec, app)
}

extend(APort, State)

APort.prototype.init = function() {
  var self = this
  var app = this.app
  var signal = this.spec
  var value, initializing = true
  this._kill = signal.fn(function push(val) {
    if (initializing) return value = val
    app.push(signal, val)
    app.recompute()
  })
  initializing = false
  this.spec = null
  this.value = value
}

APort.prototype.onkill = function() {
  this._kill && this._kill()
}

exports.Push = Push

function Push(input) {
  this.uid = newUid()
  this.input = input
}

Push.prototype.createState = function(app) {
  return new APush(this, app)
}

exports.APush = APush

function APush(spec, app) {
  State.call(this, spec, app)
}

extend(APush, State)

APush.prototype.init = function() {
  this.input = this.dependOn(this.spec.input)
}

APush.prototype.markListenersDirty = function() {}

APush.prototype.recompute = function() {
  throw new Error('This should be implemented in Clojure')
}

APush.prototype.onkill = function() {
  this.input.kill(this)
}

exports.PullOnly = PullOnly

function PullOnly(input) {
  this.uid = newUid()
  this.input = input
}

PullOnly.prototype.createState = function(app) {
  return new APullOnly(this, app)
}

function APullOnly(spec, app) {
  State.call(this, spec, app)
}

extend(APullOnly, State)

APullOnly.prototype.init = function() {
  this.input = this.dependOn(this.spec.input)
}

APullOnly.prototype.recompute = function() {
  this.value = this.input.value
}

APullOnly.prototype.markListenersDirty = function() {}

APullOnly.prototype.onkill = function() {
  this.input.kill(this)
}

exports.Effect = Effect

function Effect(fn, kill, inputs) {
  this.uid = newUid()
  this.fn = fn
  this.kill = kill
  this.inputs = inputs
}

Effect.prototype.createState = function(app) {
  return new AEffect(this, app)
}

function AEffect(spec, app) {
  State.call(this, spec, app)
}

extend(AEffect, State)

AEffect.prototype.init = function() {
  var inputs = this.spec.inputs
  this.inputs = new Array(inputs.length)
  for(var i = 0; i < inputs.length; i++) {
    this.inputs[i] = this.dependOn(inputs[i])
  }
}

AEffect.prototype.recompute = function() {
  var args = new Array(this.inputs.length)
  for(var i = 0; i < this.inputs.length; i++) {
    args[i] = this.inputs[i].value
  }
  this.spec.fn.apply(null, args)
}

AEffect.prototype.onkill = function() {
  for(var i = 0; i < this.inputs.length; i++) {
    this.inputs[i].kill(this)
  }
  this.spec.kill && this.spec.kill()
}

AEffect.prototype.markListenersDirty = function() {}

AEffect.prototype.lowerListenersPriority = function() {}

AEffect.prototype.getDownstreamPriority = function() {
  return 0
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

function time(name, f) {
  return function() {
    var begin = new Date
    f.apply(this, arguments)
    var end = new Date
    console.log(name + ': ' + (end.getTime() - begin.getTime()) + ' ms')
  }
}

})(dar.ui.frp.core)
