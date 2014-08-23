goog.provide('dar.ui.util')

;(function(exports) {

function Events(el, handler) {
  this.el = el
  this.el.__events = this
  this.listeners = {}
  this.handler = handler
}

Events.prototype.setListener = function(type, k, l) {
  var listeners = this.listeners[type]
  if (!listeners) {
    listeners = this.listeners[type] = {}
    this.el.addEventListener(type, this.handler)
  }
  listeners[k] = l
}

Events.prototype.removeListener = function(type, k) {
  var listeners = this.listeners[type]
  if (!listeners) return
  delete listeners[k]
  for(var _ in listeners) {
    return
  }
  this.listeners[type] = null
  this.el.removeEventListener(type, this.handler)
}

Events.prototype.getListeners = function(type) {
  var ret = []
  var listeners = this.listeners[type]
  for(var key in listeners) {
    ret.push(listeners[key])
  }
  return ret
}

Events.prototype.updateListener = function(type, k, l, prev) {
  if (l == null) {
    this.removeListener(type, k)
  } else {
    this.setListener(type, k, l)
  }
}

exports.events = function(el, handler) {
  return el.__events || new Events(el, handler)
}

exports.listeners = function(el, type) {
  return el.__events && el.__events.getListeners(type)
}

exports.remove = remove

function remove(el) {
  el.parentNode && el.parentNode.removeChild(el)
}

exports.replace = function(el, ref) {
  ref.parentNode && ref.parentNode.replaceChild(el, ref)
  return el
}

exports.softRemove = function(el, ms) {
  setTimeout(function() {
    remove(el)
  }, ms)
}

exports.isElement = isElement

function isElement(el) {
  switch(el.nodeType) {
    case 1:
      return !el.hasAttribute('data-virtual')
    case 3:
      return true
    default:
      return false
  }
}

exports.firstChild = function(el) {
  return firstElement(el.firstChild)
}

exports.lastChild = function(el) {
  return lastElement(el.lastChild)
}

function firstElement(el) {
  while(el) {
    if (isElement(el)) return el
    el = el.nextSibling
  }
}

function lastElement(el) {
  while(el) {
    if (isElement(el)) return el
    el = el.previousSibling
  }
}

exports.nextSibling = nextSibling

function nextSibling(el) {
  return firstElement(el.nextSibling)
}

exports.prevSibling = prevSibling

function prevSibling(el) {
  return lastElement(el.previousSibling)
}

})(dar.ui.util)