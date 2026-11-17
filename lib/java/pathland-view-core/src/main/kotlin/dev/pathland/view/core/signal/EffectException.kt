package dev.pathland.view.core.signal

/**
 * Unchecked exception thrown when an effect body throws during the synchronous
 * flush; the cause carries the original failure and the effect remains usable.
 */
class EffectException(message: String, cause: Throwable) : RuntimeException(message, cause)
