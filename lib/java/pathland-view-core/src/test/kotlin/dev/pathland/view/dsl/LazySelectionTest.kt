package dev.pathland.view.dsl

import dev.pathland.core.protocol.Categories
import dev.pathland.core.protocol.Commands
import dev.pathland.core.protocol.Properties
import dev.pathland.core.transport.Event
import dev.pathland.core.transport.FrameOpcodeSink
import dev.pathland.core.transport.ProtocolFrame
import dev.pathland.view.core.Environment
import dev.pathland.view.core.Keys
import dev.pathland.view.core.View
import dev.pathland.view.engine.Emitter
import dev.pathland.view.engine.InputDispatcher
import dev.pathland.view.engine.RenderResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LazySelectionTest {

    private data class Mounted(val sink: FrameOpcodeSink, val result: RenderResult)

    private fun mount(root: View): Mounted {
        val sink = FrameOpcodeSink()
        val result = Emitter(sink).mount(root, Environment.DEFAULT)
        return Mounted(sink, result)
    }

    private fun focusOps(frame: ProtocolFrame) =
        frame.opcodes().filter {
            it.category() == Categories.META && it.command() == Commands.Meta.SET_FOCUS
        }

    private fun focusedId(frame: ProtocolFrame): Int? =
        focusOps(frame).lastOrNull { it.b() != 0 }?.a()

    private fun focusableIds(frame: ProtocolFrame): List<Int> =
        frame.opcodes().filter {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                (it.b() and 0xffff) == Properties.EVENT_LISTENERS &&
                (it.c() and Commands.Listeners.FOCUS) != 0
        }.map { it.a() }.distinct()

    private fun texts(frame: ProtocolFrame): List<String> =
        frame.opcodes().filter {
            it.category() == Categories.PARAMETER && it.command() == Commands.Parameter.SET_TEXT
        }.map { frame.stringAt(it.b()) }

    private fun key(dispatcher: InputDispatcher, id: Int, code: Int) {
        dispatcher.dispatch(Event.keyDown(id, code, 0, 0))
    }

    @Test
    fun stackSelectionGatesFocusAndNavigates() {
        val selected = signal(0)
        val focus = signal(false)
        var tapped = -1
        var activated = -1
        val mounted = mount(
            LazyVStack(
                spacing = 0f,
                selection = LazySelection(
                    selectedIndex = selected,
                    focusState = focus,
                    onActivate = { activated = it },
                    onTap = { tapped = it },
                ),
            ) {
                ForEach((0 until 30).toList(), itemExtent = 20f) { _, index ->
                    Text("$index")
                }
            },
        )
        val dispatcher = InputDispatcher(mounted.result, signal("/"))

        val initial = mounted.sink.frame()
        assertTrue(focusOps(initial).isEmpty(), "selection does not steal focus at mount")
        assertEquals(1, focusableIds(initial).size, "one roving tab stop")

        focus.value = true
        val request = focusOps(mounted.sink.frame()).single()
        assertEquals(1, request.b())
        assertEquals(focusableIds(initial).single(), request.a())

        mounted.result.tapActions().values.toList()[2].run()
        assertEquals(tapped, selected.value, "tap reports the selected index")
        assertTrue(tapped >= 0)
        assertTrue(focus.value)

        val tappedFocus = focusedId(mounted.sink.frame())!!
        key(dispatcher, tappedFocus, Keys.ENTER)
        assertEquals(tapped, activated)

        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_DOWN)
        assertEquals(tapped + 1, selected.value)
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.END)
        assertEquals(29, selected.value)
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.HOME)
        assertEquals(0, selected.value)

        focus.value = false
        selected.value = 0
        mounted.result.visibleRanges()[mounted.result.rootId()]!!.onVisibleRange(10, 5)
        assertEquals(10, selected.value)
        assertTrue(focusOps(mounted.sink.frame()).isEmpty(), "unfocused clamp does not focus")
    }

    @Test
    fun horizontalStackUsesLeftAndRight() {
        val selected = signal(0)
        val focus = signal(true)
        val mounted = mount(
            LazyHStack(
                spacing = 0f,
                selection = LazySelection(selected, focus),
            ) {
                ForEach((0 until 10).toList(), itemExtent = 20f) { _, index ->
                    Text("$index")
                }
            },
        )
        val dispatcher = InputDispatcher(mounted.result, signal("/"))

        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_RIGHT)
        assertEquals(1, selected.value)
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_LEFT)
        assertEquals(0, selected.value)
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_LEFT)
        assertEquals(0, selected.value, "stop at left edge")
    }

    @Test
    fun verticalGridNavigatesByRowAndStopsAtEdges() {
        val selected = signal(4)
        val focus = signal(true)
        val mounted = mount(
            LazyVGrid(
                columns = 3,
                selection = LazySelection(selected, focus),
            ) {
                ForEach((0 until 100).toList(), itemExtent = 20f) { _, index ->
                    Text("$index")
                }
            },
        )
        val dispatcher = InputDispatcher(mounted.result, signal("/"))

        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_DOWN)
        assertEquals(7, selected.value)
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_RIGHT)
        assertEquals(8, selected.value)
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_RIGHT)
        assertEquals(8, selected.value, "stop at the row edge")
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_LEFT)
        assertEquals(7, selected.value)
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.END)
        assertEquals(99, selected.value)
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_DOWN)
        assertEquals(99, selected.value, "stop at the last row")

        focus.value = false
        selected.value = 0
        mounted.result.visibleRanges()[mounted.result.rootId()]!!.onVisibleRange(10, 2)
        assertEquals(30, selected.value)
        assertTrue(focusOps(mounted.sink.frame()).isEmpty(), "unfocused grid clamp does not focus")

        val pinned = mount(
            LazyVGrid(
                columns = 3,
                selection = LazySelection(signal(94), signal(false)),
            ) {
                ForEach((0 until 100).toList(), itemExtent = 20f) { _, index ->
                    Text("$index")
                }
            },
        )
        assertTrue(texts(pinned.sink.frame()).contains("94"), "selected grid unit is pinned")
    }

    @Test
    fun horizontalGridNavigatesByColumnAndStopsAtEdges() {
        val selected = signal(2)
        val focus = signal(true)
        val mounted = mount(
            LazyHGrid(
                rows = 2,
                selection = LazySelection(selected, focus),
            ) {
                ForEach((0 until 100).toList(), itemExtent = 20f) { _, index ->
                    Text("$index")
                }
            },
        )
        val dispatcher = InputDispatcher(mounted.result, signal("/"))

        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_RIGHT)
        assertEquals(4, selected.value)
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_DOWN)
        assertEquals(5, selected.value)
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_DOWN)
        assertEquals(5, selected.value, "stop at the column edge")
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_LEFT)
        assertEquals(3, selected.value)
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.END)
        assertEquals(99, selected.value)
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_RIGHT)
        assertEquals(99, selected.value, "stop at the last column")

        selected.value = 99
        assertTrue(texts(mounted.sink.frame()).contains("99"), "selected column unit is pinned")

        focus.value = false
        selected.value = 0
        mounted.result.visibleRanges()[mounted.result.rootId()]!!.onVisibleRange(5, 2)
        assertEquals(10, selected.value)
        assertTrue(focusOps(mounted.sink.frame()).isEmpty(), "unfocused grid clamp does not focus")
    }

    @Test
    fun gridTracksUseTheirConcreteSizeForSelectionNavigation() {
        val selected = signal(0)
        val focus = signal(true)
        val mounted = mount(
            LazyVGrid(
                tracks = listOf(GridItem.fixed(20f), GridItem.fixed(20f)),
                selection = LazySelection(selected, focus),
            ) {
                ForEach((0 until 10).toList(), itemExtent = 20f) { _, index ->
                    Text("$index")
                }
            },
        )
        val dispatcher = InputDispatcher(mounted.result, signal("/"))
        key(dispatcher, focusedId(mounted.sink.frame())!!, Keys.ARROW_DOWN)
        assertEquals(2, selected.value)
    }

    @Test
    fun adaptiveGridTracksRejectSelection() {
        assertThrows(IllegalArgumentException::class.java) {
            LazyVGrid(
                tracks = listOf(GridItem.adaptive(80f)),
                selection = LazySelection(signal(0), signal(false)),
            ) {
                ForEach((0 until 10).toList(), itemExtent = 20f) { _, index ->
                    Text("$index")
                }
            }
        }
    }

    @Test
    fun selectionAndExplicitPinAreMutuallyExclusive() {
        assertThrows(IllegalArgumentException::class.java) {
            LazyVStack(
                spacing = 0f,
                pin = signal(1),
                selection = LazySelection(signal(0), signal(false)),
            ) {
                ForEach((0 until 10).toList(), itemExtent = 20f) { _, index ->
                    Text("$index")
                }
            }
        }
    }

    @Test
    fun selectionRequiresASingleForEachDataSource() {
        assertThrows(IllegalArgumentException::class.java) {
            LazyVStack(
                spacing = 0f,
                selection = LazySelection(signal(0), signal(false)),
            ) {
                Text("eager")
            }
        }
    }
}
