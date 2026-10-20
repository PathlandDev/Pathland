package dev.pathland.view.signal;

/** Handle to a running effect; {@link #destroy()} unsubscribes it from the reactive graph. */
public interface EffectRef {

    /** Stop the effect: it will no longer re-run on dependency changes. */
    void destroy();
}