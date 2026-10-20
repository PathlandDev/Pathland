package dev.pathland.view.signal;

/**
 * Unchecked exception thrown when an {@code effect} body throws during the
 * synchronous flush, so the failure surfaces at the write that triggered it.
 */
public final class EffectException extends RuntimeException {

    public EffectException(String message, Throwable cause) {
        super(message, cause);
    }
}