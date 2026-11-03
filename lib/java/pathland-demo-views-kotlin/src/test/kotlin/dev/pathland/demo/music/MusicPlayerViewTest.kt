package dev.pathland.demo.music

import dev.pathland.view.core.Environment
import dev.pathland.view.core.protocol.Categories
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.core.protocol.Components
import dev.pathland.view.core.protocol.Opcode
import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.state.InMemoryStateStore
import dev.pathland.view.core.state.PersistentState
import dev.pathland.view.core.state.StateStore
import dev.pathland.view.core.transport.Event
import dev.pathland.view.core.transport.FrameOpcodeSink
import dev.pathland.view.core.transport.ProtocolFrame
import dev.pathland.view.engine.Emitter
import dev.pathland.view.engine.InputDispatcher
import dev.pathland.view.engine.RenderResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The music player demo: a track library, now-playing sidebar with album art,
 * and a bottom player bar. Asserts the rendered tree and that interactions drive
 * the persisted player state.
 */
class MusicPlayerViewTest {

    private data class Mounted(
        val result: RenderResult,
        val sink: FrameOpcodeSink,
        val store: StateStore,
    )

    private class MultiFrameSink : FrameOpcodeSink() {
        val frames = mutableListOf<ProtocolFrame>()

        override fun endFrame() {
            super.endFrame()
            frames.add(frame())
        }
    }

    private fun mount(snapshot: Boolean = false): Mounted {
        val store = InMemoryStateStore()
        val state = PersistentState(store, "session-1")
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        if (snapshot) emitter.snapshotEmission(true)
        val result = emitter.mount(MusicPlayerView.make(), Environment(state))
        return Mounted(result, sink, store)
    }

    private fun mountAd(): Mounted {
        val store = InMemoryStateStore()
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        emitter.snapshotEmission(true)
        val result = emitter.mount(
            MusicPlayerView.make("http://localhost:8080/ad"),
            Environment(PersistentState(store, "session-1")),
        )
        return Mounted(result, sink, store)
    }

    @Test
    fun libraryRendersTracksAndAlbumArt() {
        val frame = mount().sink.frame()

        assertTrue(anySetText(frame, "Building on Solid Ground"))
        assertTrue(anySetText(frame, "Rendered Free"))
        assertTrue(anySetText(frame, "The Pathland Dream"))
        assertTrue(anySetText(frame, "The Foundation"))
        assertFalse(anySetText(frame, "Now Playing"))
        assertTrue(anySetText(frame, "Library"))
        assertTrue(countCreate(frame, Components.IMAGE) >= 3)
        assertTrue(anySetPropertyString(frame, Properties.IMAGE_SOURCE, COVER))
        assertEquals(2, countCreate(frame, Components.SIZE_THAT_FITS))
    }

    @Test
    fun fitShowsTheSidebarWhenTheParentIsWideEnough() {
        val mounted = mount()
        val slotId = slotIdByMaxThreshold(mounted.sink.frame(), 1024f)
        assertFalse(anySetText(mounted.sink.frame(), "Now Playing"))

        mounted.result.fitInputs()[slotId]!!.accept(1)
        val wide = mounted.sink.frame()

        assertTrue(anySetText(wide, "Now Playing"))
        assertTrue(anySetText(wide, "Up Next"))
        assertEquals(0, countCreate(wide, Components.MEDIA_CONTROLLER))
        assertEquals(0, countCreate(wide, Components.SIZE_THAT_FITS))
        assertTrue(countCreateAnything(wide) < 40)
    }

    @Test
    fun playerBarRendersTransportAndSliders() {
        val mounted = mount()
        val frame = mounted.sink.frame()

        assertTrue(countCreate(frame, Components.BUTTON) >= 3)
        assertEquals(1, countCreate(frame, Components.SLIDER))
        assertTrue(anySlider(frame, 0f, 100f))
        assertTrue(anySetText(frame, "|"))
        assertTrue(anySetPropertyString(
            frame,
            Properties.MEDIA_SOURCE,
            "/_pathland/assets/audio/track1.mp3",
        ))
        assertTrue(anySetProperty(frame, Properties.PLAYBACK_STATE, 0))
        assertTrue(anySetProperty(frame, Properties.MEDIA_POSITION, 0f))

        val barSlot = slotIdByMaxThreshold(frame, 700f)
        mounted.result.fitInputs()[barSlot]!!.accept(1)
        val wideBar = mounted.sink.frame()
        assertEquals(1, countCreate(wideBar, Components.SLIDER))
        assertTrue(anySlider(wideBar, 0f, 1f))
        assertTrue(anySliderValue(wideBar, 0.7f))

        val rootSlot = slotIdByMaxThreshold(frame, 1024f)
        mounted.result.fitInputs()[rootSlot]!!.accept(1)
        val withSidebar = mounted.sink.frame()
        assertFalse(anySetText(withSidebar, "|"))
        assertEquals(0, countCreate(withSidebar, Components.SLIDER))
        assertTrue(countCreateAnything(withSidebar) < 40)
    }

    @Test
    fun snapshotEmissionCarriesBothCandidates() {
        val mounted = mount(snapshot = true)
        val frame = mounted.sink.frame()

        assertTrue(anySetText(frame, "Library"))
        assertTrue(anySetText(frame, "Now Playing"))
        val rootSlot = slotIdByMaxThreshold(frame, 1024f)
        assertEquals(2, childCount(frame, rootSlot))
    }

    @Test
    fun interactionDrivesPersistedPlayerState() {
        val mounted = mount()
        for (tap in mounted.result.tapActions().values.toList()) {
            tap.run()
        }
        val track = mounted.store.load("player.track:session-1", Integer::class.java)
        val playing = mounted.store.load("player.playing:session-1", java.lang.Boolean::class.java)
        assertTrue(track.isPresent || playing.isPresent)
    }

    @Test
    fun mediaEventsDriveThePlayerState() {
        val store = InMemoryStateStore()
        val state = PersistentState(store, "session-1")
        val sink = FrameOpcodeSink()
        val result = Emitter(sink).mount(MusicPlayerView.make(), Environment(state))
        val dispatcher = InputDispatcher(result, Signals.signal("/"))

        val audioId = result.mediaInputs().keys.first()
        dispatcher.dispatch(Event.mediaTimeUpdated(audioId, 12.5f))
        assertEquals(12.5f, store.load("player.position:session-1", java.lang.Float::class.java).get())

        val media = result.mediaInputs().values.first()
        media.onVolumeChanged(0.5f)
        assertEquals(0.5f, store.load("player.volume:session-1", java.lang.Float::class.java).get())
        media.onEnded()
        assertTrue(store.load("player.track:session-1", Integer::class.java).isPresent)
    }

    @Test
    fun endedAdvancesWithoutPausing() {
        val store = InMemoryStateStore()
        store.save("player.playing:session-1", true)
        val state = PersistentState(store, "session-1")
        val sink = MultiFrameSink()
        val result = Emitter(sink).mount(MusicPlayerView.make(), Environment(state))
        val before = sink.frames.size

        result.mediaInputs().values.first().onEnded()

        val deltas = sink.frames.subList(before, sink.frames.size)
        assertTrue(deltas.any {
            anySetPropertyString(it, Properties.MEDIA_SOURCE, MusicPlayerView.TRACKS[1].audio)
        })
        assertFalse(deltas.any { anyProperty(it, Properties.PLAYBACK_STATE) })
    }

    @Test
    fun timeUpdatesDoNotEchoBackAsMediaPosition() {
        val store = InMemoryStateStore()
        val state = PersistentState(store, "session-3")
        val sink = FrameOpcodeSink()
        val result = Emitter(sink).mount(MusicPlayerView.make(), Environment(state))
        val dispatcher = InputDispatcher(result, Signals.signal("/"))
        val audioId = result.mediaInputs().keys.first()

        dispatcher.dispatch(Event.mediaTimeUpdated(audioId, 30f))
        assertFalse(anyProperty(sink.frame(), Properties.MEDIA_POSITION))
    }

    @Test
    fun timeReportAdvancesTheSeekBarValue() {
        val store = InMemoryStateStore()
        val state = PersistentState(store, "session-1")
        val sink = FrameOpcodeSink()
        val result = Emitter(sink).mount(MusicPlayerView.make(), Environment(state))
        val seekId = seekSliderId(sink.frame())
        val dispatcher = InputDispatcher(result, Signals.signal("/"))
        val audioId = result.mediaInputs().keys.first()

        dispatcher.dispatch(Event.mediaTimeUpdated(audioId, 30f))
        val delta = sink.frame()
        assertEquals(
            30f / MusicPlayerView.TRACKS[0].duration * 100f,
            propertyFloat(delta, seekId, Properties.VALUE),
            0.05f,
        )
    }

    @Test
    fun sponsoredSlotProxiesTheAdAppOnlyWhenConfigured() {
        val plain = mount(snapshot = true)
        assertEquals(0, countCreate(plain.sink.frame(), Components.PROXY_VIEW))
        assertTrue(plain.result.proxyViews().isEmpty())

        val ad = mountAd()
        assertEquals(1, countCreate(ad.sink.frame(), Components.PROXY_VIEW))
        assertEquals(1, ad.result.proxyViews().size)
        assertEquals(
            "http://localhost:8080/ad",
            ad.result.proxyViews().values.first().endpoint(),
        )
    }

    private fun countCreate(frame: ProtocolFrame, component: Int): Int =
        frame.opcodes().count {
            it.category() == Categories.TREE &&
                it.command() == Commands.Tree.CREATE_NODE &&
                it.b() == component
        }

    private fun countCreateAnything(frame: ProtocolFrame): Int =
        frame.opcodes().count {
            it.category() == Categories.TREE && it.command() == Commands.Tree.CREATE_NODE
        }

    private fun childCount(frame: ProtocolFrame, parentId: Int): Int =
        frame.opcodes().count {
            it.category() == Categories.TREE &&
                it.command() == Commands.Tree.INSERT_CHILD &&
                it.a() == parentId
        }

    private fun anySetText(frame: ProtocolFrame, text: String): Boolean =
        frame.opcodes().any {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_TEXT &&
                text == frame.stringAt(it.b())
        }

    private fun anySetProperty(frame: ProtocolFrame, property: Int, value: Int): Boolean =
        frame.opcodes().any {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                (it.b() and 0xFFFF) == property &&
                it.c() == value
        }

    private fun anySetProperty(frame: ProtocolFrame, property: Int, value: Float): Boolean =
        frame.opcodes().any {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                (it.b() and 0xFFFF) == property &&
                java.lang.Float.intBitsToFloat(it.c()) == value
        }

    private fun anyProperty(frame: ProtocolFrame, property: Int): Boolean =
        frame.opcodes().any {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                (it.b() and 0xFFFF) == property
        }

    private fun anySetPropertyString(frame: ProtocolFrame, property: Int, value: String): Boolean =
        frame.opcodes().any {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                (it.b() and 0xFFFF) == property &&
                value == frame.stringAt(it.c())
        }

    private fun anySlider(frame: ProtocolFrame, min: Float, max: Float): Boolean =
        frame.opcodes().any {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                (it.b() and 0xFFFF) == Properties.MIN_VALUE &&
                java.lang.Float.intBitsToFloat(it.c()) == min &&
                hasProperty(frame, Properties.MAX_VALUE, max)
        }

    private fun anySliderValue(frame: ProtocolFrame, value: Float): Boolean =
        frame.opcodes().any {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                (it.b() and 0xFFFF) == Properties.VALUE &&
                java.lang.Float.intBitsToFloat(it.c()) == value
        }

    private fun hasProperty(frame: ProtocolFrame, property: Int, value: Float): Boolean =
        frame.opcodes().any {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                (it.b() and 0xFFFF) == property &&
                java.lang.Float.intBitsToFloat(it.c()) == value
        }

    private fun propertyFloat(frame: ProtocolFrame, nodeId: Int, property: Int): Float {
        for (op: Opcode in frame.opcodes()) {
            if (op.category() == Categories.PARAMETER &&
                op.command() == Commands.Parameter.SET_PROPERTY &&
                op.a() == nodeId &&
                (op.b() and 0xFFFF) == property
            ) {
                return java.lang.Float.intBitsToFloat(op.c())
            }
        }
        return Float.NaN
    }

    private fun seekSliderId(frame: ProtocolFrame): Int {
        for (op: Opcode in frame.opcodes()) {
            if (op.category() == Categories.PARAMETER &&
                op.command() == Commands.Parameter.SET_PROPERTY &&
                (op.b() and 0xFFFF) == Properties.MAX_VALUE &&
                java.lang.Float.intBitsToFloat(op.c()) == 100f
            ) {
                return op.a()
            }
        }
        throw AssertionError("the 0..100 seek slider is not in the mount frame")
    }

    private fun fitThresholds(frame: ProtocolFrame, slotId: Int): FloatArray {
        for (op: Opcode in frame.opcodes()) {
            if (op.category() == Categories.PARAMETER &&
                op.command() == Commands.Parameter.SET_PROPERTY &&
                op.a() == slotId &&
                (op.b() and 0xFFFF) == Properties.FIT_QUERY
            ) {
                val strings = frame.strings()
                val offset = op.c()
                val count = le32(strings, offset)
                return FloatArray(count) {
                    java.lang.Float.intBitsToFloat(le32(strings, offset + 4 + it * 4))
                }
            }
        }
        throw AssertionError("no FIT_QUERY for slot $slotId")
    }

    private fun slotIdByMaxThreshold(frame: ProtocolFrame, maxThreshold: Float): Int {
        for (op: Opcode in frame.opcodes()) {
            if (op.category() == Categories.TREE &&
                op.command() == Commands.Tree.CREATE_NODE &&
                op.b() == Components.SIZE_THAT_FITS
            ) {
                val thresholds = fitThresholds(frame, op.a())
                if (thresholds.last() == maxThreshold) return op.a()
            }
        }
        throw AssertionError("no fit slot with max threshold $maxThreshold")
    }

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private companion object {
        const val COVER = "/_pathland/assets/albumart/cover1.jpg"
    }
}
