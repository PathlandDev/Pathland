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
import dev.pathland.view.core.transport.Event
import dev.pathland.view.core.transport.FrameOpcodeSink
import dev.pathland.view.core.transport.ProtocolFrame
import dev.pathland.view.engine.Emitter
import dev.pathland.view.engine.InputDispatcher
import dev.pathland.view.engine.RenderResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression coverage for the responsive MusicPlayerView: crossing a
 * `SIZE_THAT_FITS` band must only swap application views. The pinned
 * `MEDIA_CONTROLLER` must keep its node id, its `MEDIA_SOURCE`, and its routing,
 * so the browser never tears down the controller's `<video>` element.
 */
class MusicPlayerFitMediaTest {

    private class RecordingSink : FrameOpcodeSink() {
        val frames = mutableListOf<ProtocolFrame>()

        override fun endFrame() {
            super.endFrame()
            frames.add(frame())
        }
    }

    private class Mounted(val result: RenderResult, val sink: RecordingSink) {
        val initial: ProtocolFrame get() = sink.frames.first()
    }

    private fun mountSnapshot(): Mounted {
        val sink = RecordingSink()
        val emitter = Emitter(sink)
        emitter.snapshotEmission(true)
        val result = emitter.mount(
            MusicPlayerView.make(),
            Environment(PersistentState(InMemoryStateStore(), "session-1")),
        )
        return Mounted(result, sink)
    }

    private fun controllerId(frame: ProtocolFrame): Int =
        frame.opcodes().first {
            it.category() == Categories.TREE &&
                it.command() == Commands.Tree.CREATE_NODE &&
                it.b() == Components.MEDIA_CONTROLLER
        }.a()

    private fun fitSlotId(frame: ProtocolFrame, maxThreshold: Float): Int {
        for (op: Opcode in frame.opcodes()) {
            if (op.category() == Categories.TREE &&
                op.command() == Commands.Tree.CREATE_NODE &&
                op.b() == Components.SIZE_THAT_FITS
            ) {
                if (fitThresholds(frame, op.a()).last() == maxThreshold) return op.a()
            }
        }
        throw AssertionError("no fit slot with max threshold $maxThreshold")
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

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun applyFit(mounted: Mounted, slotId: Int, index: Int): List<ProtocolFrame> {
        val before = mounted.sink.frames.size
        mounted.result.fitInputs()[slotId]!!.accept(index)
        return mounted.sink.frames.drop(before)
    }

    private fun assertControllerNotTornDown(frames: List<ProtocolFrame>, id: Int) {
        for (frame in frames) {
            for (op: Opcode in frame.opcodes()) {
                if (op.category() != Categories.TREE) continue
                when (op.command()) {
                    Commands.Tree.CREATE_NODE ->
                        assertFalse(
                            op.b() == Components.MEDIA_CONTROLLER || op.a() == id,
                            "a fit change re-created the pinned controller node: $op",
                        )
                    Commands.Tree.DELETE_NODE ->
                        assertFalse(op.a() == id, "a fit change deleted the pinned controller node: $op")
                    Commands.Tree.REMOVE_CHILD ->
                        assertFalse(op.b() == id, "a fit change removed the pinned controller child: $op")
                }
            }
            assertFalse(
                frame.opcodes().any {
                    it.category() == Categories.PARAMETER &&
                        it.command() == Commands.Parameter.SET_PROPERTY &&
                        it.a() == id &&
                        (it.b() and 0xFFFF) == Properties.MEDIA_SOURCE
                },
                "a fit change re-emitted MEDIA_SOURCE on the pinned controller",
            )
        }
    }

    @Test
    fun snapshotFitDowngradeKeepsThePinnedController() {
        val mounted = mountSnapshot()
        val id = controllerId(mounted.initial)
        val slot = fitSlotId(mounted.initial, 1024f)
        assertTrue(mounted.result.mediaInputs().containsKey(id), "the controller media sink is registered")

        val deltas = applyFit(mounted, slot, 2)

        assertControllerNotTornDown(deltas, id)
        assertTrue(
            mounted.result.mediaInputs().containsKey(id),
            "the controller media sink survives the snapshot -> live downgrade",
        )
    }

    @Test
    fun everyFitCrossingKeepsTheSamePinnedController() {
        val mounted = mountSnapshot()
        val id = controllerId(mounted.initial)
        val slot = fitSlotId(mounted.initial, 1024f)
        for (index in listOf(2, 0, 1, 2, 0)) {
            val deltas = applyFit(mounted, slot, index)
            assertControllerNotTornDown(deltas, id)
            assertTrue(
                mounted.result.mediaInputs().containsKey(id),
                "the controller routing is lost after fit index $index",
            )
        }
    }

    @Test
    fun transportStillWorksAfterAFitCrossing() {
        val mounted = mountSnapshot()
        val id = controllerId(mounted.initial)
        val slot = fitSlotId(mounted.initial, 1024f)
        for (index in listOf(2, 0, 2)) applyFit(mounted, slot, index)

        val dispatcher = InputDispatcher(mounted.result, Signals.signal("/"))
        val beforeMedia = mounted.sink.frames.size
        dispatcher.dispatch(Event.mediaTimeUpdated(id, 30f))
        val mediaDeltas = mounted.sink.frames.drop(beforeMedia)
        assertTrue(
            mediaDeltas.any { frame ->
                frame.opcodes().any {
                    it.category() == Categories.PARAMETER &&
                        it.command() == Commands.Parameter.SET_PROPERTY &&
                        (it.b() and 0xFFFF) == Properties.VALUE
                }
            },
            "a MEDIA_TIME_UPDATED report no longer reaches the seek bar after a fit crossing",
        )

        // The nested transport fit's snapshot form emits a play button for every
        // candidate; only the surviving/attached one still has a tap action.
        val playId = lastLabelId(mounted.sink.frames, "Play", mounted.result.tapActions().keys)
        assertTrue(mounted.result.tapActions().containsKey(playId), "the play button is attached")
        val beforePlay = mounted.sink.frames.size
        dispatcher.dispatch(Event.pointerUp(playId, 0f, 0f))
        val playDeltas = mounted.sink.frames.drop(beforePlay)
        assertTrue(
            playDeltas.any { frame ->
                frame.opcodes().any {
                    it.category() == Categories.PARAMETER &&
                        it.command() == Commands.Parameter.SET_PROPERTY &&
                        it.a() == id &&
                        (it.b() and 0xFFFF) == Properties.PLAYBACK_STATE &&
                        it.c() == 1
                }
            },
            "the play button no longer targets the pinned controller after a fit crossing",
        )
    }

    private fun lastLabelId(frames: List<ProtocolFrame>, label: String, attached: Set<Int>): Int {
        var id = 0
        for (frame in frames) {
            for (op: Opcode in frame.opcodes()) {
                if (op.category() == Categories.PARAMETER &&
                    op.command() == Commands.Parameter.SET_PROPERTY &&
                    (op.b() and 0xFFFF) == Properties.LABEL &&
                    frame.stringAt(op.c()) == label &&
                    op.a() in attached
                ) {
                    id = op.a()
                }
            }
        }
        check(id != 0) { "no attached node labelled '$label' in the frame stream" }
        return id
    }
}
