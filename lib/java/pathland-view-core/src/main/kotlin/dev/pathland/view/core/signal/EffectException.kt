package dev.pathland.view.core.signal

/** Unchecked exception thrown when an effect body throws during the synchronous flush. */
class EffectException(message: String, cause: Throwable) : RuntimeException(message, cause)
