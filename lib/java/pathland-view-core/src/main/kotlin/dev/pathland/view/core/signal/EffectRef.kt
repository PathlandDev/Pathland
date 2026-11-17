package dev.pathland.view.core.signal

/**
 * Handle to a running effect; `destroy` unsubscribes it from the reactive graph.
 *
 * After destruction the effect no longer schedules or runs; destroying twice is
 * harmless.
 */
interface EffectRef {
    /** Stop the effect: it will no longer re-run on dependency changes. */
    fun destroy()
}
