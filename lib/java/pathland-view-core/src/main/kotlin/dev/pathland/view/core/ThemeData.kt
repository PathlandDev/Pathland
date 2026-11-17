package dev.pathland.view.core

import dev.pathland.core.transport.OpcodeSink

/**
 * A renderer-wide design-token override payload.
 *
 * Implementations emit only `PARAMETER::SET_DESIGN_TOKEN` opcodes; the caller
 * controls frame boundaries. The host supplies one when constructing the
 * emitter, and `replaceTheme` resets then re-emits it.
 */
interface ThemeData {
    /** Emit the overrides into an already-open frame (no frame boundaries). */
    fun emitInto(sink: OpcodeSink)

    /** Emit all overrides as one frame of `PARAMETER::SET_DESIGN_TOKEN` opcodes. */
    fun emit(sink: OpcodeSink) {
        sink.beginFrame()
        emitInto(sink)
        sink.endFrame()
    }
}
