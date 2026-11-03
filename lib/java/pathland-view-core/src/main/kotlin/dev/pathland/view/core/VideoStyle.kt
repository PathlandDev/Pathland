package dev.pathland.view.core

/** A video style: turns a video surface's controller into the app-defined control UI. */
interface VideoStyle : Style {
    fun makeBody(config: Configuration): View?

    override fun body(content: View): View =
        EnvironmentView.of(content, Environment.VIDEO_STYLE, this)

    /** The media configuration of the video being styled. */
    data class Configuration(val controller: MediaController?) {
        fun controller(): MediaController? = controller
    }
}
