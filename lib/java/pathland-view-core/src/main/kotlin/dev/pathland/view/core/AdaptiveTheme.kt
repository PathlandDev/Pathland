package dev.pathland.view.core

import dev.pathland.view.core.transport.OpcodeSink

/** An adaptive theme: a light and a dark Theme pair. */
class AdaptiveTheme(
    private val light: Theme,
    private val dark: Theme,
) : ThemeData {
    fun light(): Theme = light
    fun dark(): Theme = dark

    override fun emitInto(sink: OpcodeSink) {
        light.emitInto(sink)
        for (o in dark.overrides()) {
            sink.setDesignToken(dark(o.path), o.valueType, o.value)
        }
    }

    private fun dark(path: String): String =
        if (path.startsWith("dark.")) path else "dark.$path"
}
