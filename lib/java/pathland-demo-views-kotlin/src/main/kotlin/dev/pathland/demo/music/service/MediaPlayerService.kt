package dev.pathland.demo.music.service

import dev.pathland.demo.music.model.Track
import dev.pathland.view.core.Environment
import dev.pathland.view.core.EnvironmentKey
import dev.pathland.view.core.MediaController
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.signal.WritableSignal

/**
 * The demo's app-owned media-player service. It owns the playlist/track state,
 * the six persisted player signals, and the [MediaController] transport. Views
 * resolve it from the environment through [MediaPlayerService.KEY].
 *
 * The service is created once per root/session; it must never be a global
 * singleton (signals are single-threaded and per-session).
 */
internal class MediaPlayerService {

    val trackIndex: WritableSignal<Int> = Signals.state("player.track", 0)
    val playing: WritableSignal<Boolean> = Signals.state("player.playing", false)
    val position: WritableSignal<Float> = Signals.state("player.position", 0f)
    val volume: WritableSignal<Float> = Signals.state("player.volume", 0.7f)
    val seekRequest: WritableSignal<Float> = Signals.state("player.seekRequest", 0f)
    val volumeRequest: WritableSignal<Float> = Signals.state("player.volumeRequest", 0.7f)

    val controller = MediaController()
    val tracks: List<Track> = TRACKS
    val currentTrack: Signal<Track> = Signals.computed { at(trackIndex.get()) }

    init {
        controller.register()
        controller.source(Signals.computed { currentTrack.get().audio })
        controller.playing(playing)
        controller.position(seekRequest)
        controller.volume(volumeRequest)
        controller.time(position)
        controller.volumeReport(volume)
        controller.onEnded { ended() }
    }


    /** Select a track and start it from the top. */
    fun selectTrack(index: Int) {
        trackIndex.set(index)
        position.set(0f)
        seekRequest.set(0f)
        playing.set(true)
    }

    /** Previous track (wraps around), restarting from the top. */
    fun previous() {
        trackIndex.set((trackIndex.get() - 1 + TRACKS.size) % TRACKS.size)
        controller.seek(0f)
    }

    /** Next track (wraps around), restarting from the top. */
    fun next() {
        trackIndex.set((trackIndex.get() + 1) % TRACKS.size)
        controller.seek(0f)
    }

    fun togglePlay() {
        controller.toggle()
    }

    /** The track [offset] places after the current one (wraps around). */
    fun trackAt(offset: Int): Track = at(trackIndex.get() + offset)

    private fun ended() {
        trackIndex.set((trackIndex.get() + 1) % TRACKS.size)
        seekRequest.set(0f)
        position.set(0f)
    }

    companion object {
        /** The environment key for the active [MediaPlayerService]. */
        val KEY: EnvironmentKey<MediaPlayerService> =
            EnvironmentKey.of("mediaPlayerService")

        /** The demo library: eight Pathland concept tracks, one album each. */
        internal val TRACKS: List<Track> = listOf(
            Track(
                "Building on Solid Ground", "The Foundation", "The Open Horizon", 178f,
                "/_pathland/assets/albumart/cover1.jpg",
                "/_pathland/assets/audio/track1.mp3",
            ),
            Track(
                "Pathland Crossing", "The Crossings", "Native Operations", 166f,
                "/_pathland/assets/albumart/cover2.jpg",
                "/_pathland/assets/audio/track2.mp3",
            ),
            Track(
                "Rendered Free", "Rena Render", "Grace From The Source", 182f,
                "/_pathland/assets/albumart/cover3.jpg",
                "/_pathland/assets/audio/track3.mp3",
            ),
            Track(
                "Rendered In Your Arms", "Ember Frame", "Architectures of Grace", 160f,
                "/_pathland/assets/albumart/cover4.jpg",
                "/_pathland/assets/audio/track4.mp3",
            ),
            Track(
                "Sixty Frames Per Second", "Rowan Frame", "The Protocol Sessions", 164f,
                "/_pathland/assets/albumart/cover5.jpg",
                "/_pathland/assets/audio/track5.mp3",
            ),
            Track(
                "The Pathland Dream", "Neon Protocol", "Velvet Horizons", 170f,
                "/_pathland/assets/albumart/cover6.jpg",
                "/_pathland/assets/audio/track6.mp3",
            ),
            Track(
                "Across the Open Land", "Open Land", "The Weight of Open Air", 156f,
                "/_pathland/assets/albumart/cover7.jpg",
                "/_pathland/assets/audio/track7.mp3",
            ),
            Track(
                "Sixteen Bytes", "The Byte Ensemble", "The Ring Buffer Sessions", 182f,
                "/_pathland/assets/albumart/cover8.jpg",
                "/_pathland/assets/audio/track8.mp3",
            ),
        )

        /** The track at an index (wraps around; safe for any signed index). */
        internal fun at(index: Int): Track = TRACKS[Math.floorMod(index, TRACKS.size)]

        /** Format seconds as `m:ss`. */
        internal fun fmt(seconds: Float): String {
            val s = maxOf(0, Math.round(seconds))
            return (s / 60).toString() + ":" + String.format("%02d", s % 60)
        }

    }
}

/** Resolve the demo's player service from the current environment. */
internal fun mediaPlayerService(): MediaPlayerService =
    Environment.value(MediaPlayerService.KEY).get()
