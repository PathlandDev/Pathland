package dev.pathland.demo.qt

import com.sun.jna.Memory
import dev.pathland.demo.DemoTheme
import dev.pathland.demo.assets.DemoAssets
import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.view.core.Environment
import dev.pathland.view.core.ffm.PathlandCore
import dev.pathland.view.core.ffm.RingOpcodeSink
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.state.InMemoryStateStore
import dev.pathland.view.core.state.PersistentState
import dev.pathland.view.engine.Emitter
import dev.pathland.view.engine.InputDispatcher

/**
 * Runs the shared Kotlin [MusicPlayerView] under the native Qt Quick renderer.
 *
 * The Kotlin DSL mounts the view through the fine-grained [Emitter] into a
 * `libpathland_core` shared ring ([RingOpcodeSink], zero-copy). The in-process
 * Qt renderer pumps the same ring in place (`pathland_qt_run_ring`); native
 * inputs round-trip as `EVENT` opcodes that this host drains and dispatches
 * through [InputDispatcher] into the app's bindings.
 *
 * No Swing, no Qt API — only Pathland authoring.
 */
object QtHost {

    /** Max events drained per wake (× 16 bytes each). */
    private const val MAX_EVENTS = 64
    private const val WINDOW_WIDTH = 1180
    private const val WINDOW_HEIGHT = 800

    @JvmStatic
    fun main(args: Array<String>) {
        val core = PathlandCore.instance()
        RingOpcodeSink(core).use { sink ->
            val handle = sink.handle()

            // The session's state + theme, exactly as the server session seeds them.
            val state = PersistentState(InMemoryStateStore(), "desktop")
            // A placeholder host path signal: MusicPlayerView has no router.
            val activePath = Signals.signal("/")

            val emitter = Emitter(sink, DemoTheme.adaptive())
            val result = emitter.mount(MusicPlayerView.make(), Environment(state))
            val dispatcher = InputDispatcher(result, activePath)
            val reader = RingEventReader(core.ringPtr(handle), core.ringLen(handle))

            // Wake callback: drain raw EVENT opcodes and route them into the app's
            // bindings. Re-emission happens on the Qt main thread, the only writer.
            val onEvent = QtRendererNative.EventCallback {
                val buf = Memory(MAX_EVENTS.toLong() * 16)
                val n = core.drainEvents(handle, buf, MAX_EVENTS)
                if (n > 0) {
                    dispatcher.dispatch(reader.decode(buf.getByteArray(0, n * 16)))
                }
            }

            val renderer = QtRendererNative.instance()
            renderer.setAssetRoot(DemoAssets.extractToTemp().toString())

            // Blocks until the window closes; the ring (handle) must outlive it.
            renderer.runRing(core.ringMut(handle), onEvent, WINDOW_WIDTH, WINDOW_HEIGHT)
        }
    }
}
