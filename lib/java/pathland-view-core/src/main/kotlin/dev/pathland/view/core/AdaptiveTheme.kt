package dev.pathland.view.core

import dev.pathland.core.transport.OpcodeSink

/**
 * A light/dark pair of design-token override sets.
 *
 * The light set is emitted as-is; each dark override is prefixed with `dark.`
 * so the renderer resolves it only in the dark scheme (an explicit `dark.`
 * prefix forces the dark value in either scheme). Emitted when the session
 * mounts or the theme is replaced.
 */
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
