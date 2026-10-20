package dev.pathland.view.signal;

/**
 * Options controlling an {@code effect}.
 *
 * <p>Mirrors Angular's {@code effect(fn, { allowSignalWrites, manualCleanup })}.
 *
 * @param allowSignalWrites whether the effect may write to signals. Defaults to
 *                          {@code false}: writes inside an effect throw, preventing
 *                          write/read cycles.
 * @param manualCleanup     when {@code true} the effect is not scheduled on creation
 *                          (the caller must run it); reserved for lifecycle-managed
 *                          frameworks.
 */
public record EffectOptions(boolean allowSignalWrites, boolean manualCleanup) {

    /** Default: no signal writes, effect scheduled immediately on creation. */
    public static final EffectOptions DEFAULT = new EffectOptions(false, false);

    /** An effect that may write to signals. */
    public static EffectOptions allowWrites() {
        return new EffectOptions(true, false);
    }
}