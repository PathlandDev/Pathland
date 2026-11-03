package dev.pathland.view.core.signal

/**
 * Options controlling an effect (mirrors Angular's
 * `effect(fn, { allowSignalWrites, manualCleanup })`).
 */
data class EffectOptions(
    val allowSignalWrites: Boolean,
    val manualCleanup: Boolean,
) {
    fun allowSignalWrites(): Boolean = allowSignalWrites
    fun manualCleanup(): Boolean = manualCleanup

    companion object {
        /** Default: no signal writes, effect scheduled immediately on creation. */
        @JvmField
        val DEFAULT = EffectOptions(false, false)

        /** An effect that may write to signals. */
        @JvmStatic
        fun allowWrites(): EffectOptions = EffectOptions(true, false)
    }
}
