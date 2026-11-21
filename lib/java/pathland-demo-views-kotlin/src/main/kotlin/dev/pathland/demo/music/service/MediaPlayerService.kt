package dev.pathland.demo.music.service

import dev.pathland.demo.music.model.Track
import dev.pathland.view.core.Environment
import dev.pathland.view.core.EnvironmentKey
import dev.pathland.view.core.MediaController
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.data.Page
import dev.pathland.view.data.PagedDataSource

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

    /**
     * Ephemeral keyboard focus in the library (not persisted): the roving tab
     * stop and lazy-window pin. Kept on the shared service so it survives the
     * root `ViewThatFits` candidate swaps.
     */
    val focusedIndex: WritableSignal<Int> = Signals.signal(0)
    val listFocused: WritableSignal<Boolean> = Signals.signal(false)

    val controller = MediaController()
    /** The demo library: the base tracks repeated to [LIBRARY_SIZE] entries. */
    val tracks: List<Track> = List(LIBRARY_SIZE) { TRACKS[it % TRACKS.size] }
    val currentTrack: Signal<Track> = Signals.computed { at(trackIndex.get()) }

    /**
     * The paged library source used by the live/SSR session. Unit-test mounts
     * (no session context) keep the in-memory [tracks] list instead.
     */
    val librarySource: PagedDataSource<Track> by lazy {
        PagedDataSource.ofBlocking(LIBRARY_PAGE_SIZE, { offset, limit -> page(offset, limit) })
    }

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
        trackIndex.set((trackIndex.get() - 1 + tracks.size) % tracks.size)
        controller.seek(0f)
    }

    /** Next track (wraps around), restarting from the top. */
    fun next() {
        trackIndex.set((trackIndex.get() + 1) % tracks.size)
        controller.seek(0f)
    }

    fun togglePlay() {
        controller.toggle()
    }

    /**
     * One page of the in-memory library (the demo's "database"). A small delay
     * makes the loading/placeholder path visible in the browser.
     */
    fun page(offset: Int, limit: Int): Page<Track> {
        if (PAGE_DELAY_MILLIS > 0) {
            try {
                Thread.sleep(PAGE_DELAY_MILLIS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        val start = offset.coerceIn(0, tracks.size)
        val end = (offset + limit).coerceIn(start, tracks.size)
        val items = if (start == end) emptyList() else tracks.subList(start, end)
        return Page(items, total = tracks.size, hasMore = end < tracks.size)
    }

    /** The track at an index (wraps around; safe for any signed index). */
    private fun at(index: Int): Track = tracks[Math.floorMod(index, tracks.size)]

    /** The track [offset] places after the current one (wraps around). */
    fun trackAt(offset: Int): Track = at(trackIndex.get() + offset)

    private fun ended() {
        trackIndex.set((trackIndex.get() + 1) % tracks.size)
        seekRequest.set(0f)
        position.set(0f)
    }

    companion object {
        /** The number of repeated library entries (the demo's long list). */
        const val LIBRARY_SIZE = 1000

        /** The paged library page size (rows loaded per page). */
        const val LIBRARY_PAGE_SIZE = 50

        /** A small simulated data-source latency for the demo's loading state. */
        private const val PAGE_DELAY_MILLIS = 40L

        /** The environment key for the active [MediaPlayerService]. */
        val KEY: EnvironmentKey<MediaPlayerService> =
            EnvironmentKey.of("mediaPlayerService")

        /** The demo library: eight Pathland concept tracks, one album each. */
        internal val TRACKS: List<Track> = listOf(
            Track(
                "Building on Solid Ground", "The Foundation", "The Open Horizon", 178f,
                "/_pathland/assets/albumart/cover1.jpg",
                "/_pathland/assets/albumart/thumbs/cover1.jpg",
                "/_pathland/assets/audio/track1.mp3",
            ),
            Track(
                "Pathland Crossing", "The Crossings", "Native Operations", 166f,
                "/_pathland/assets/albumart/cover2.jpg",
                "/_pathland/assets/albumart/thumbs/cover2.jpg",
                "/_pathland/assets/audio/track2.mp3",
            ),
            Track(
                "Rendered Free", "Rena Render", "Grace From The Source", 182f,
                "/_pathland/assets/albumart/cover3.jpg",
                "/_pathland/assets/albumart/thumbs/cover3.jpg",
                "/_pathland/assets/audio/track3.mp3",
            ),
            Track(
                "Rendered In Your Arms", "Ember Frame", "Architectures of Grace", 160f,
                "/_pathland/assets/albumart/cover4.jpg",
                "/_pathland/assets/albumart/thumbs/cover4.jpg",
                "/_pathland/assets/audio/track4.mp3",
            ),
            Track(
                "Sixty Frames Per Second", "Rowan Frame", "The Protocol Sessions", 164f,
                "/_pathland/assets/albumart/cover5.jpg",
                "/_pathland/assets/albumart/thumbs/cover5.jpg",
                "/_pathland/assets/audio/track5.mp3",
            ),
            Track(
                "The Pathland Dream", "Neon Protocol", "Velvet Horizons", 170f,
                "/_pathland/assets/albumart/cover6.jpg",
                "/_pathland/assets/albumart/thumbs/cover6.jpg",
                "/_pathland/assets/audio/track6.mp3",
            ),
            Track(
                "Across the Open Land", "Open Land", "The Weight of Open Air", 156f,
                "/_pathland/assets/albumart/cover7.jpg",
                "/_pathland/assets/albumart/thumbs/cover7.jpg",
                "/_pathland/assets/audio/track7.mp3",
            ),
            Track(
                "Sixteen Bytes", "The Byte Ensemble", "The Ring Buffer Sessions", 182f,
                "/_pathland/assets/albumart/cover8.jpg",
                "/_pathland/assets/albumart/thumbs/cover8.jpg",
                "/_pathland/assets/audio/track8.mp3",
            ),
        )

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
