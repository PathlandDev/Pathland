package dev.pathland.view.dsl

import dev.pathland.view.core.signal.EffectRef
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.core.subscription.GlobalSubject
import dev.pathland.view.core.subscription.Topic

/** A writable signal (Angular-style). */
fun <T> signal(initial: T): WritableSignal<T> = Signals.signal(initial)

/** A named writable signal (names surface in dependency errors). */
fun <T> signal(name: String, initial: T): WritableSignal<T> = Signals.signal(name, initial)

/** A writable signal with a custom equality comparator. */
fun <T> signal(initial: T, equal: (T, T) -> Boolean): WritableSignal<T> =
    Signals.signal(initial) { a, b -> equal(a, b) }

/** A lazy, memoized derived signal. */
fun <T> computed(block: () -> T): Signal<T> = Signals.computed { block() }

/** An immutable signal carrying a static value. */
fun <T> constant(value: T): Signal<T> = Signals.constant(value)

/**
 * Subscribe to [topic] for [subject], returning the session's local signal.
 * `val name = topic(Customer.NAME, customerId, initialName)`.
 */
fun <T, S> topic(key: Topic<T, S>, subject: S, initial: T): Signal<T> =
    Signals.topic(key, subject, initial)

/**
 * Subscribe to an unscoped (global) topic:
 * `val events = topic(AppTopics.EVENTS, 0L)`.
 */
fun <T> topic(key: Topic<T, GlobalSubject>, initial: T): Signal<T> =
    Signals.topic(key, initial)

/** Register an effect; runs immediately, then on tracked dependency changes. */
fun effect(block: () -> Unit): EffectRef = Signals.effect { block() }

/** Read signals without recording dependencies on the enclosing context. */
fun <T> untracked(block: () -> T): T = Signals.untracked { block() }

/** The current value (SwiftUI-shaped signal read). */
val <T> Signal<T>.value: T
    get() = get()

/** A two-way signal value: `count.value = 1`, `count.value += 1`. */
var <T> WritableSignal<T>.value: T
    get() = get()
    set(value) = set(value)

/** Derive a read-only signal from this one. */
fun <T, R> Signal<T>.map(transform: (T) -> R): Signal<R> =
    Signals.computed { transform(get()) }

/** A read-only view of a writable signal. */
fun <T> WritableSignal<T>.readonly(): Signal<T> = asReadonly()
