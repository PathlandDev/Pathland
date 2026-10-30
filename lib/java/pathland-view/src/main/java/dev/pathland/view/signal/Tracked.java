package dev.pathland.view.signal;

/**
 * A synchronous tracked computation: the supplier runs immediately (so a parent
 * render can use its value), records the signals it reads, and re-runs the supplier
 * when one of them changes. The re-computed value is handed to the registered
 * consumer.
 *
 * <p>This is the emitter's per-component render primitive: each component slot runs
 * its render as a {@code Tracked<PathlandNode>} and reconciles the fresh node tree on
 * every re-run. It is public only because the emitter lives in a different package;
 * application code should use {@link Signals#effect} / {@link Signals#computed}.
 *
 * @param <T> the computed value type
 */
public final class Tracked<T> implements EffectRef {

    private final T initial;
    private final EffectRef effect;

    Tracked(T initial, EffectRef effect) {
        this.initial = initial;
        this.effect = effect;
    }

    /** The value produced by the synchronous first run. */
    public T initial() {
        return initial;
    }

    @Override
    public void destroy() {
        effect.destroy();
    }
}
