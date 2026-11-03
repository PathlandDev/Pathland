package dev.pathland.view.core

import dev.pathland.view.core.protocol.Components
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.engine.PathlandNode

/** A media playback controller: owns a media resource and its transport as signals. */
class MediaController {

    private var source: Signal<String> = Signals.constant("")

    private var playing: WritableSignal<Boolean> = Signals.signal(false)
    private var position: WritableSignal<Float> = Signals.signal(0f)
    private var volume: WritableSignal<Float> = Signals.signal(1f)
    private var muted: WritableSignal<Boolean> = Signals.signal(false)
    private var loop: WritableSignal<Boolean> = Signals.signal(false)
    private var rate: WritableSignal<Float> = Signals.signal(1f)

    private var playingReport: WritableSignal<Boolean> = Signals.signal(false)
    private var time: WritableSignal<Float> = Signals.signal(0f)
    private var duration: WritableSignal<Float> = Signals.signal(0f)
    private var volumeReport: WritableSignal<Float> = Signals.signal(1f)
    private var onEnded: Runnable = Runnable { }

    /** A controller with no resource. */
    constructor()

    /** A controller for a static resource. */
    constructor(source: String?) {
        source(source)
    }

    /** Set a static resource. */
    fun source(source: String?): MediaController {
        this.source = Signals.constant(source ?: "")
        return this
    }

    /** Bind the resource to a reactive signal. */
    fun source(source: Signal<String>): MediaController {
        this.source = source
        return this
    }

    /** Advance when the media ends. */
    fun onEnded(onEnded: Runnable?): MediaController {
        this.onEnded = onEnded ?: Runnable { }
        return this
    }

    fun playing(signal: WritableSignal<Boolean>): MediaController {
        this.playing = signal
        return this
    }

    fun position(signal: WritableSignal<Float>): MediaController {
        this.position = signal
        return this
    }

    fun volume(signal: WritableSignal<Float>): MediaController {
        this.volume = signal
        return this
    }

    fun muted(signal: WritableSignal<Boolean>): MediaController {
        this.muted = signal
        return this
    }

    fun loop(signal: WritableSignal<Boolean>): MediaController {
        this.loop = signal
        return this
    }

    fun rate(signal: WritableSignal<Float>): MediaController {
        this.rate = signal
        return this
    }

    fun time(signal: WritableSignal<Float>): MediaController {
        this.time = signal
        return this
    }

    fun duration(signal: WritableSignal<Float>): MediaController {
        this.duration = signal
        return this
    }

    fun playingReport(signal: WritableSignal<Boolean>): MediaController {
        this.playingReport = signal
        return this
    }

    fun volumeReport(signal: WritableSignal<Float>): MediaController {
        this.volumeReport = signal
        return this
    }

    fun play() = playing.set(true)
    fun pause() = playing.set(false)
    fun toggle() = playing.update { v -> !v }
    fun seek(seconds: Float) = position.set(seconds)
    fun setVolume(value: Float) = volume.set(maxOf(0f, minOf(1f, value)))
    fun setMuted(value: Boolean) = muted.set(value)
    fun setLoop(value: Boolean) = loop.set(value)
    fun setRate(value: Float) = rate.set(value)

    fun sourceSignal(): Signal<String> = source
    fun playing(): WritableSignal<Boolean> = playing
    fun position(): WritableSignal<Float> = position
    fun volume(): WritableSignal<Float> = volume
    fun muted(): WritableSignal<Boolean> = muted
    fun loop(): WritableSignal<Boolean> = loop
    fun rate(): WritableSignal<Float> = rate
    fun time(): WritableSignal<Float> = time
    fun duration(): WritableSignal<Float> = duration
    fun playingReport(): WritableSignal<Boolean> = playingReport
    fun volumeReport(): WritableSignal<Float> = volumeReport
    fun onEnded(): Runnable = onEnded

    /**
     * A non-visual marker that materializes this controller's `MEDIA_CONTROLLER`
     * node in the tree (needed for pure-audio playback).
     */
    fun attach(): View = AttachMarker(this)

    private class AttachMarker(private val controller: MediaController) : View {
        override fun renderNode(env: Environment): PathlandNode {
            val node = PathlandNode(Components.COMMENT)
            node.mediaControllerRef = controller
            return node
        }
    }
}
