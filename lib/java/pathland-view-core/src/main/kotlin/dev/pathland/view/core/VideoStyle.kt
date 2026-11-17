package dev.pathland.view.core

/**
 * The control-UI strategy for a `VIDEO` surface.
 *
 * The style receives the surface's `MediaController` and returns the app-defined
 * controls. The DSL resolves the nearest `VideoStyle` from the `VIDEO_STYLE`
 * environment key; `NativeVideoStyle` renders no content so the renderer's own
 * playback chrome can be used.
 */
interface VideoStyle : Style {
    fun makeBody(config: Configuration): View?

    override fun body(content: View): View =
        EnvironmentView.of(content, Environment.VIDEO_STYLE, this)

    /** The controller the video surface is bound to (null when unattached). */
    data class Configuration(val controller: MediaController?) {
        fun controller(): MediaController? = controller
    }
}
