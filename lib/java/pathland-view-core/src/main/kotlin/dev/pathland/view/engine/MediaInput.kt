package dev.pathland.view.engine

/** A host-side sink for the media events a bound MEDIA_CONTROLLER reports. */
interface MediaInput {
    fun onPlayStateChanged(playing: Boolean) {}
    fun onTimeUpdated(seconds: Float) {}
    fun onDurationChanged(seconds: Float) {}
    fun onEnded() {}
    fun onVolumeChanged(volume: Float) {}
}
