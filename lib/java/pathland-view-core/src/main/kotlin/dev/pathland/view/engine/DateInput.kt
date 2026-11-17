package dev.pathland.view.engine

/**
 * A host-side sink for a `DATE_CHANGED` event.
 *
 * The value is days-since-epoch plus millis-of-day; the host routes it to the
 * app's bound date signal. It is not a protocol callback.
 */
fun interface DateInput {
    fun accept(days: Int, millisOfDay: Int)
}
