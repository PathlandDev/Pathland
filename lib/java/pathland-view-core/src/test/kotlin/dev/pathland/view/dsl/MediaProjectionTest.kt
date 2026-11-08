package dev.pathland.view.dsl

import dev.pathland.view.core.Environment
import dev.pathland.view.core.MediaController
import dev.pathland.view.core.View
import dev.pathland.core.protocol.Categories
import dev.pathland.core.protocol.Commands
import dev.pathland.core.protocol.Components
import dev.pathland.core.protocol.Properties
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.core.transport.FrameOpcodeSink
import dev.pathland.core.transport.ProtocolFrame
import dev.pathland.view.engine.Emitter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaProjectionTest {

    private class RecordingSink : FrameOpcodeSink() {
        val frames = mutableListOf<ProtocolFrame>()

        override fun endFrame() {
            super.endFrame()
            frames.add(frame())
        }
    }

    private class AudioRoot(
        private val controller: MediaController,
        private val tick: WritableSignal<Int>? = null,
    ) : Component() {
        override fun body(): View {
            controller.register()
            return VStack {
                Text("audio")
                tick?.let { Text("t${it.get()}") }
            }
        }
    }

    private fun createdComponents(frame: ProtocolFrame): List<Int> =
        frame.opcodes()
            .filter { it.category() == Categories.TREE && it.command() == Commands.Tree.CREATE_NODE }
            .map { it.b() }

    private fun nodeId(frame: ProtocolFrame, component: Int): Int =
        frame.opcodes().first {
            it.category() == Categories.TREE &&
                it.command() == Commands.Tree.CREATE_NODE &&
                it.b() == component
        }.a()

    private fun hasProperty(frame: ProtocolFrame, node: Int, property: Int, value: Int): Boolean =
        frame.opcodes().any {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                it.a() == node &&
                (it.b() and 0xFFFF) == property &&
                it.c() == value
        }

    @Test
    fun registeredAudioControllerGetsAPinnedRootNode() {
        val controller = MediaController("/track.mp3")
        val sink = FrameOpcodeSink()
        Emitter(sink).mount(AudioRoot(controller), Environment.DEFAULT)

        val frame = sink.frame()
        assertEquals(1, createdComponents(frame).count { it == Components.MEDIA_CONTROLLER })
        val controllerId = nodeId(frame, Components.MEDIA_CONTROLLER)
        val rootId = nodeId(frame, Components.VSTACK)
        assertTrue(
            frame.opcodes().any {
                it.category() == Categories.TREE &&
                    it.command() == Commands.Tree.INSERT_CHILD &&
                    it.a() == rootId && it.b() == controllerId
            },
            "the controller is a root child",
        )
        assertTrue(
            frame.opcodes().any {
                it.category() == Categories.PARAMETER &&
                    it.command() == Commands.Parameter.SET_PROPERTY &&
                    it.a() == controllerId &&
                    (it.b() and 0xFFFF) == Properties.MEDIA_SOURCE &&
                    frame.stringAt(it.c()) == "/track.mp3"
            },
            "the controller carries its resource",
        )
    }

    @Test
    fun videoStaysInPlaceAndBindsTheRootController() {
        val controller = MediaController("https://example.com/clip.mp4")
        val sink = FrameOpcodeSink()
        Emitter(sink).mount(
            VStack {
                Text("before")
                Video(controller)
                Text("after")
            },
            Environment.DEFAULT,
        )

        val frame = sink.frame()
        val created = createdComponents(frame)
        val videoIndex = created.indexOf(Components.VIDEO)
        val controllerIndex = created.indexOf(Components.MEDIA_CONTROLLER)
        assertTrue(videoIndex in 0 until controllerIndex, "video keeps its authored position")
        val videoId = nodeId(frame, Components.VIDEO)
        val controllerId = nodeId(frame, Components.MEDIA_CONTROLLER)
        assertTrue(
            hasProperty(frame, videoId, Properties.MEDIA_CONTROLLER_ID, controllerId),
            "video references the root controller",
        )
    }

    @Test
    fun controllerIdSurvivesRootReRendersAndPropertyDeltasTargetIt() {
        val controller = MediaController("/track.mp3")
        val tick = Signals.signal(0)
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        emitter.mount(AudioRoot(controller, tick), Environment.DEFAULT)

        val controllerId = nodeId(sink.frame(), Components.MEDIA_CONTROLLER)
        tick.set(1)
        val delta = sink.frame()
        assertFalse(
            delta.opcodes().any {
                it.category() == Categories.TREE &&
                    (it.command() == Commands.Tree.CREATE_NODE ||
                        it.command() == Commands.Tree.DELETE_NODE) &&
                    (it.a() == controllerId || it.b() == Components.MEDIA_CONTROLLER)
            },
            "a root re-render must not recreate the controller",
        )
        assertTrue(delta.opcodes().any {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_TEXT &&
                delta.stringAt(it.b()) == "t1"
        })

        controller.play()
        val propertyDelta = sink.frame()
        assertTrue(
            hasProperty(propertyDelta, controllerId, Properties.PLAYBACK_STATE, 1),
            "transport changes target the pinned controller node",
        )
    }

    @Test
    fun videoAddedInANestedBranchProjectsItsControllerAfterMount() {
        val controller = MediaController("https://example.com/late.mp4")
        val sink = RecordingSink()

        class Branch(private val controller: MediaController) : Component() {
            val show = Signals.signal(false)
            override fun body(): View = VStack {
                if (show.get()) Video(controller) else Text("none")
            }
        }

        val branch = Branch(controller)
        Emitter(sink).mount(VStack { Text("root"); +branch }, Environment.DEFAULT)
        assertTrue(sink.frames.none { Components.MEDIA_CONTROLLER in createdComponents(it) })

        branch.show.set(true)
        val frames = sink.frames.toList()
        val controllerFrame = frames.first { Components.MEDIA_CONTROLLER in createdComponents(it) }
        val videoFrame = frames.last { Components.VIDEO in createdComponents(it) }
        val controllerId = nodeId(controllerFrame, Components.MEDIA_CONTROLLER)
        val videoId = nodeId(videoFrame, Components.VIDEO)
        assertTrue(
            hasProperty(videoFrame, videoId, Properties.MEDIA_CONTROLLER_ID, controllerId),
            "a late video binds the projected controller",
        )
    }

    @Test
    fun unregisteringRemovesTheRootController() {
        val controller = MediaController("/track.mp3")
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        emitter.registerMediaController(controller)
        emitter.mount(VStack { Text("audio") }, Environment.DEFAULT)
        val controllerId = nodeId(sink.frame(), Components.MEDIA_CONTROLLER)

        emitter.unregisterMediaController(controller)
        val delta = sink.frame()
        assertTrue(
            delta.opcodes().any {
                it.category() == Categories.TREE &&
                    it.command() == Commands.Tree.DELETE_NODE &&
                    it.a() == controllerId
            },
            "unregister removes the controller node",
        )
    }

}
