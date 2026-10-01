package com.pathland.view;

import com.pathland.view.emit.OpcodeSink;

/**
 * A set of **global, renderer-wide design-token overrides**
 * ({@code PARAMETER::SET_DESIGN_TOKEN}, spec/TOKENS.md). Overrides are renderer
 * state, so they never re-emit nodes and never participate in the diff.
 *
 * <p>Two implementations exist: a single-value {@link Theme} (light/one scheme)
 * and an {@link AdaptiveTheme} composed of a light + dark {@link Theme} pair.
 */
public interface ThemeData {

    /** Emit the overrides into an already-open frame (no frame boundaries). */
    void emitInto(OpcodeSink sink);

    /** Emit all overrides as one frame of {@code PARAMETER::SET_DESIGN_TOKEN} opcodes. */
    default void emit(OpcodeSink sink) {
        sink.beginFrame();
        emitInto(sink);
        sink.endFrame();
    }
}