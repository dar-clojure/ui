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
  s.receive(v)
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

State.prototype.receive = function(v) {
  if (this.value === v) return
  this.value = v
  if (this.event) this.app.events.push(this)
  this.markListenersDirty()
}

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

State.prototype.lowerPriority = function(priority) {
  if (this.priority <= priority) return false
  if (this.lowering) throw new Error('Cycle in the signal graph')
  this.lowering = true
  this.priority = priority
  var touchedDirty = this.updateListenersPriority()
  this.lowering = false
  return this.dirty || touchedDirty
}

State.prototype.updateListenersPriority = function() {
  return this.lowerListenersPriority(this.getDownstreamPriority())
}

State.prototype.lowerListenersPriority = function(priority) {
  var ret = false
  for(var key in this.listeners) {
    var touchedDirty = this.listeners[key].lowerPriority(priority)
    ret = ret || touchedDirty
  }
  return ret
}

State.prototype.kill = function(listener) {
  if (listener) this.removeListener(listener)
  if (this.hasListeners()) return
  delete this.app.signals[this.uid]
  this.killed = true
  this.onkill()
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

exports.Port = Port

function Port(fn, input) {
  this.uid = newUid()
  this.fn = fn
  this.input = input
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
  this.initializing = true
  this.spec.fn(this)
  this.initializing = false
  if (this.spec.input)
    this.input = this.dependOn(this.spec.input)
}

APort.prototype.push = function(val) {
  if (this.initializing) {
    this.value = val
  } else if (this.tick) {
    throw new Error('You can push a new value only in the next tick')
  } else {
    this.app.push(this.spec, val)
    this.app.recompute()
  }
}

APort.prototype.onInput = function(f) {
  this._oninput = f
}

APort.prototype.onKill = function(f) {
  this._kill = f
}

APort.prototype.onkill = function() {
  this._kill && this._kill()
}

APort.prototype.recompute = function() {
  if (!this._oninput || !this.input) return
  this.tick = true
  this._oninput(this.input.value)
  this.tick = false
}

APort.prototype.getDownstreamPriority = function() {
  return 0
}

exports.Pipe = Pipe

function Pipe(target, src) {
  this.uid = newUid()
  this.src = src
  this.target = target
}

Pipe.prototype.createState = function(app) {
  return new APipe(this, app)
}

exports.APipe = APipe

function APipe(spec, app) {
  State.call(this, spec, app)
}

extend(APipe, State)

APipe.prototype.init = function() {
  this.src = this.dependOn(this.spec.src)
  this.target = this.app.state(this.spec.target)
  this.target.lowerPriority(this.priority - 1) && this.app.queue.resort()
  this.targetListener = new DummyListener
  this.target.addListener(this.targetListener)
}

APipe.prototype.markListenersDirty = function() {}

APipe.prototype.getDownstreamPriority = function() {
  return 0
}

APipe.prototype.updateListenersPriority = function() {
  this.target.lowerPriority(this.priority - 1)
}

APipe.prototype.recompute = function() {
  this.target.push(this.src.value)
}

APipe.prototype.onkill = function() {
  this.src.kill(this)
  this.target.kill(this.targetListener)
}

function DummyListener() {
  this.uid = newUid()
}

DummyListener.prototype.markDirty = function() {}

DummyListener.prototype.lowerPriority = function() {}

exports.PullOnly = PullOnly

function PullOnly(input) {
  this.uid = newUid()
  this.input = input
  this.event = this.input.event
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

exports.Hold = Hold

function Hold(inputs) {
  this.uid = newUid()
  this.inputs = inputs
}

Hold.prototype.createState = function(app) {
  return new AHold(this, app)
}

function AHold(spec, app) {
  State.call(this, spec, app)
}

AHold.prototype.init = function() {
  var inputs = this.spec.inputs
  this.inputs = new Array(inputs.length)
  for(var i = 0; i < inputs.length; i++) {
    this.inputs[0] = this.dependOn(inputs[i])
  }
  this.priority = 0
}

AHold.prototype.kill = function() {
  for(var i = 0; i < this.inputs.length; i++) {
    this.inputs[i].kill(this)
  }
}

AHold.prototype.markDirty = function() {}

AHold.prototype.getDownstreamPriority = function() {
  return 0
}

AHold.prototype.lowerPriority = function() {
  return false
}

exports.Switch = Switch

function Switch(initial, switchEvent) {
  this.uid = newUid()
  this.initial = initial
  this.switchEvent = switchEvent
}

Switch.prototype.createState = function(app) {
  return new ASwitch(this, app)
}

function ASwitch(spec, app) {
  State.call(this, spec, app)
}

ASwitch.prototype.init = function() {
  this.upstream = this.dependOn(this.spec.initial)
  this.event = this.upstream.event
  this.switchEvent = this.dependOn(this.spec.switchEvent)
  this.priority = this.switchEvent.getDownstreamPriority()
  this.spec = null // do not leak stuff
}

ASwitch.prototype.getDownstreamPriority = function() {
  return this.switchEvent
    ? Math.min(this.switchEvent.getDownstreamPriority(), this.upstream.getDownstreamPriority()) - 1
    : this.upstream.getDownstreamPriority() - 1
}

ASwitch.prototype.recompute = function() {
  if (!this.switchEvent) {
    // we are done with switching and just retranslating upstream
    this.value = this.upstream.value
    return
  }

  if (!this.switchEvent.value) {
    if (this.priority <= this.upstream.getDownstreamPriority()) {
      this.value = this.upstream.value
      this.priority = this.switchEvent.getDownstreamPriority()
    } else {
      this.priority = this.upstream.getDownstreamPriority()
      this.markDirty()
    }
    return
  }

  var next = this.switchEvent.value
    , dp = this.getDownstreamPriority()
    , upstream
    , switchEvent

  if (next instanceof Switch) {
    upstream = this.dependOn(next.initial)
    switchEvent = this.dependOn(next.switchEvent)
  } else {
    upstream = this.dependOn(next)
  }

  // it is important that we first subscribed for new signals
  // and only then are killing old ones

  if (upstream !== this.upstream) {
    this.upstream.kill(this)
    this.upstream = upstream
    this.event = this.upstream.event
  }

  if (switchEvent !== this.switchEvent) {
    this.switchEvent.kill(this)
    this.switchEvent = switchEvent
  }

  if (this.switchEvent) {
    this.priority = this.switchEvent.getDownstreamPriority()
  } else {
    this.priority = this.upstream.getDownstreamPriority()
  }

  if (this.getDownstreamPriority() < dp && this.lowerListenersPriority(this.getDownstreamPriority())) {
    this.app.queue.resort()
  }

  this.markDirty()
}

exports.MapSwitch = MapSwitch

function MapSwitch(sf, input) {
  this.uid = newUid()
  this.sf = sf
  this.input = input
}

MapSwitch.prototype.createState = function(app) {
  return new AMapSwitch(this, app)
}

exports.AMapSwitch = AMapSwitch

function AMapSwitch(spec, app) {
  State.call(this, spec, app)
}

AMapSwitch.prototype.init = function() {
  this.input
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
