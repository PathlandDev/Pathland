package dev.pathland.view.core

import dev.pathland.core.transport.OpcodeSink

/** A set of global, renderer-wide design-token overrides. */
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
