package dev.pathland.view.dsl

import dev.pathland.core.text.AttributedString
import dev.pathland.core.protocol.Categories
import dev.pathland.core.protocol.Commands
import dev.pathland.core.protocol.Components
import dev.pathland.view.core.Environment
import dev.pathland.view.core.Keys
import dev.pathland.core.protocol.Properties
import dev.pathland.view.core.View
import dev.pathland.view.engine.Emitter
import dev.pathland.core.transport.FrameOpcodeSink
import dev.pathland.core.protocol.Opcode
import dev.pathland.core.transport.Event
import dev.pathland.core.transport.ProtocolFrame
import dev.pathland.view.engine.InputDispatcher
import dev.pathland.view.engine.KeyEvent
import dev.pathland.view.engine.RenderResult
import dev.pathland.view.core.state.InMemoryStateStore
import dev.pathland.view.core.state.PersistentState
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DslTest {

    private fun mount(root: View, env: Environment = Environment.DEFAULT): Pair<RenderResult, ProtocolFrame> {
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        val result = emitter.mount(root, env)
        return result to sink.frame()
    }

    private fun texts(frame: ProtocolFrame): List<String> =
        frame.opcodes()
            .filter { it.category() == Categories.PARAMETER && it.command() == Commands.Parameter.SET_TEXT }
            .map { frame.stringAt(it.b()) }

    private fun properties(frame: ProtocolFrame): List<Int> =
        frame.opcodes()
            .filter { it.category() == Categories.PARAMETER && it.command() == Commands.Parameter.SET_PROPERTY }
            .map { it.b() and 0xffff }

    @Test
    fun kotlinTreeEmitsTheExpectedStructure() {
        val root = VStack(spacing = 8f) {
            Text("Hello")
            Button("Tap") {}
        }

        val (_, frame) = mount(root)
        val created = frame.opcodes()
            .filter { it.category() == Categories.TREE && it.command() == Commands.Tree.CREATE_NODE }
            .map { it.b() and 0xffff }
        assertEquals(listOf(Components.VSTACK, Components.TEXT, Components.BUTTON, Components.TEXT), created)
        assertEquals(listOf("Hello", "Tap"), texts(frame))
    }

    @Test
    fun attributedTextEmitsSetAttributedTextInsteadOfSetText() {
        val value = AttributedString.builder().append("Hi").bold(0, 2).build()
        val (_, frame) = mount(Text(value))
        assertEquals(0, frame.opcodes().count { it.command() == Commands.Parameter.SET_TEXT })
        val ops = frame.opcodes().filter {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_ATTRIBUTED_TEXT
        }
        assertEquals(1, ops.size)
        assertTrue(ops[0].b() >= 0)
    }

    @Test
    fun coreOwnedControlsEmitTheirProperties() {
        val name = signal("")
        val on = signal(true)
        val value = signal(0.5f)
        val color = signal(dev.pathland.view.core.Color.RED)

        val (_, frame) = mount(VStack {
            TextField("Name", name)
            Toggle("On", on)
            Slider(value, 0f..1f)
            ColorPicker(color)
        })

        val created = frame.opcodes()
            .filter { it.category() == Categories.TREE && it.command() == Commands.Tree.CREATE_NODE }
            .map { it.b() and 0xffff }
        assertEquals(listOf(Components.VSTACK, Components.TEXT_FIELD, Components.TOGGLE,
            Components.SLIDER, Components.COLOR_PICKER), created)
        assertTrue(properties(frame).contains(Properties.PROMPT))
        assertTrue(properties(frame).contains(Properties.SELECTED))
        assertTrue(properties(frame).contains(Properties.VALUE))
        assertTrue(properties(frame).contains(Properties.COLOR_VALUE))
    }

    @Test
    fun childModifiersApplyInsideABuilder() {
        val root = VStack { Text("x").fontSize(20f) }
        val (_, frame) = mount(root)
        assertTrue(
            properties(frame).contains(Properties.FONT_SIZE),
            "a modifier on a child must replace the auto-registered leaf, not be dropped",
        )
    }

    @Test
    fun trailingBlocksAutoCollectNestedChildren() {
        val root = VStack(spacing = 4f) {
            Text("a")
            HStack(spacing = 2f) {
                Text("b")
                Text("c")
            }
        }
        val (_, frame) = mount(root)
        assertEquals(listOf("a", "b", "c"), texts(frame))
    }

    @Test
    fun customViewConstructedInABlockAutoRegisters() {
        class Greeting : Component() {
            override fun body(): View = Text("hi")
        }

        val (_, frame) = mount(VStack { Greeting() })
        assertEquals(listOf("hi"), texts(frame))
    }

    @Test
    fun modifierSugarEmitsTheExpectedProperties() {
        val sugared = Text("x").padding(16f).frame(width = 100f, height = 20f)
        val (_, sugaredFrame) = mount(sugared)
        assertTrue(properties(sugaredFrame).contains(Properties.PADDING))
        assertTrue(properties(sugaredFrame).contains(Properties.WIDTH))
        assertTrue(properties(sugaredFrame).contains(Properties.HEIGHT))
    }

    @Test
    fun labelEmitsIconThenTitle() {
        val (_, frame) = mount(Label("Home", "home"))
        val created = frame.opcodes()
            .filter { it.category() == Categories.TREE && it.command() == Commands.Tree.CREATE_NODE }
            .map { it.b() and 0xffff }
        assertEquals(listOf(0x11, 0x02, 0x01), created, "label = HStack(image, text)")
    }

    @Test
    fun compositesEmitTheExpectedStructure() {
        val root = VStack {
            Label("Home", "home")
            ViewThatFits(Fit(Text("a")), Fit(Text("b"), 640f))
            ProxyView("https://origin.example", "/music", "svc")
        }

        val (result, frame) = mount(root)
        val created = frame.opcodes()
            .filter { it.category() == Categories.TREE && it.command() == Commands.Tree.CREATE_NODE }
            .map { it.b() and 0xffff }
        assertTrue(created.contains(Components.VIEW_THAT_FITS))
        assertEquals(1, result.proxyViews().size, "the proxy node exposes its config")
        assertEquals("https://origin.example", result.proxyViews().values.single().endpoint())
    }

    @Test
    fun signalTextIsAFineGrainedDelta() {
        val label = signal("one")
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        emitter.mount(VStack { Text(label) }, Environment.DEFAULT)

        label.value = "two"
        val delta = sink.frame()
        assertEquals(1, delta.opcodes().size, "a bound text emits exactly one SET_TEXT")
        assertEquals("two", texts(delta).single())
    }

    @Test
    fun textParsesInlineMarkdownByDefault() {
        val (_, rich) = mount(Text("**Hi**"))
        assertTrue(rich.opcodes().any { it.command() == Commands.Parameter.SET_ATTRIBUTED_TEXT })
        assertTrue(rich.opcodes().none { it.command() == Commands.Parameter.SET_TEXT })

        val (_, plain) = mount(Text("Hi"))
        assertTrue(plain.opcodes().any { it.command() == Commands.Parameter.SET_TEXT })
        assertTrue(plain.opcodes().none { it.command() == Commands.Parameter.SET_ATTRIBUTED_TEXT })

        val (_, literal) = mount(Text("**Hi**", markdown = false))
        assertTrue(literal.opcodes().any { it.command() == Commands.Parameter.SET_TEXT })
        assertTrue(literal.opcodes().none { it.command() == Commands.Parameter.SET_ATTRIBUTED_TEXT })
    }

    @Test
    fun reactiveTextParsesMarkdownAndFallsBackToPlain() {
        val label = signal("**Hi**")
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        emitter.mount(Text(label), Environment.DEFAULT)
        assertTrue(sink.frame().opcodes().any { it.command() == Commands.Parameter.SET_ATTRIBUTED_TEXT })

        label.value = "plain"
        val plain = sink.frame()
        assertEquals(1, plain.opcodes().size)
        assertEquals(Commands.Parameter.SET_TEXT, plain.opcodes().single().command())

        label.value = "__Bold__"
        val rich = sink.frame()
        assertEquals(1, rich.opcodes().size)
        assertEquals(Commands.Parameter.SET_ATTRIBUTED_TEXT, rich.opcodes().single().command())
    }

    @Test
    fun stateDelegateIsPersistedAndReactive() {
        class Counter : Component() {
            var count by state(0)

            override fun body(): View = VStack {
                Text("Count: $count")
                Button("increment") { count++ }
            }
        }

        val store = InMemoryStateStore()
        val session = PersistentState(store, "s")
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        val result = emitter.mount(Counter(), Environment(session))

        assertEquals(listOf("Count: 0", "increment"), texts(sink.frame()))
        val increment = result.tapActions().values.single()
        increment.run()

        assertEquals(listOf("Count: 1"), texts(sink.frame()))
        session.close()

        val restored = PersistentState(store, "s")
        val (_, restoredFrame) = mount(Counter(), Environment(restored))
        assertEquals(listOf("Count: 1", "increment"), texts(restoredFrame), "state is reloaded by property name")
        restored.close()
    }

    @Test
    fun reactiveForEachReconcilesRows() {
        val items = signal(listOf("a", "b"))
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        emitter.mount(VStack { ForEach(items) { item, _ -> Text(item) } }, Environment.DEFAULT)

        items.value = listOf("a", "b", "c")
        val delta = sink.frame()
        assertEquals(1, delta.opcodes().count {
            it.category() == Categories.TREE && it.command() == Commands.Tree.CREATE_NODE
        })
        assertEquals("c", texts(delta).single())
    }

    @Test
    // @ref spec/DSL.md#34-structural-reactivity-reactive-bodies--slots [tests]
    fun eagerForEachRowsAreDirectStackChildren() {
        val (_, frame) = mount(
            VStack(spacing = 8f) {
                Text("before")
                ForEach(listOf("one", "two")) { item, _ -> Text(item) }
                Text("after")
            },
        )
        val created = frame.opcodes()
            .filter { it.category() == Categories.TREE && it.command() == Commands.Tree.CREATE_NODE }
            .map { it.b() and 0xffff }
        assertEquals(
            listOf(Components.VSTACK, Components.TEXT, Components.TEXT, Components.TEXT, Components.TEXT),
            created,
        )
        assertEquals(listOf("before", "one", "two", "after"), texts(frame))

        val (_, horizontalFrame) = mount(
            HStack(spacing = 8f) {
                Text("before")
                ForEach(listOf("one", "two")) { item, _ -> Text(item) }
                Text("after")
            },
        )
        val horizontalComponents = horizontalFrame.opcodes()
            .filter { it.category() == Categories.TREE && it.command() == Commands.Tree.CREATE_NODE }
            .map { it.b() and 0xffff }
        assertEquals(
            listOf(Components.HSTACK, Components.TEXT, Components.TEXT, Components.TEXT, Components.TEXT),
            horizontalComponents,
        )
    }

    @Test
    fun lazyStackEmitsAWindowedDataSource() {
        val items = signal((0 until 100).map { "item-$it" })
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        val result = emitter.mount(
            LazyVStack(spacing = 0f) {
                ForEach(items) { item, _ -> Text(item).frame(height = 44f) }
            },
            Environment.DEFAULT,
        )
        val frame = sink.frame()
        val props = frame.opcodes().filter {
            it.category() == Categories.PARAMETER && it.command() == Commands.Parameter.SET_PROPERTY
        }
        fun property(id: Int): Int? = props.firstOrNull { (it.b() and 0xffff) == id }?.c()
        assertEquals(100, property(Properties.ITEM_COUNT))
        assertEquals(44f, Float.fromBits(property(Properties.ITEM_EXTENT)!!))
        assertEquals(0, property(Properties.FIRST_ITEM_INDEX))
        // Default first window (20) + overscan (2).
        assertEquals(22, texts(frame).size)

        result.visibleRanges()[result.rootId()]!!.onVisibleRange(50, 5)
        val delta = sink.frame()
        assertEquals(9, delta.opcodes().count {
            it.category() == Categories.TREE && it.command() == Commands.Tree.CREATE_NODE
        })
        assertEquals(48, delta.opcodes().first {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                (it.b() and 0xffff) == Properties.FIRST_ITEM_INDEX
        }.c())
    }

    @Test
    fun lazyVGridEmitsARowWindow() {
        val items = signal((0 until 100).map { "cell-$it" })
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        val result = emitter.mount(
            LazyVGrid(columns = 3, spacing = 0f) {
                ForEach(items) { item, _ -> Text(item).frame(height = 44f) }
            },
            Environment.DEFAULT,
        )
        val frame = sink.frame()
        val props = frame.opcodes().filter {
            it.category() == Categories.PARAMETER && it.command() == Commands.Parameter.SET_PROPERTY
        }
        fun property(id: Int): Int? = props.firstOrNull { (it.b() and 0xffff) == id }?.c()
        // ceil(100 cells / 3 columns) = 34 rows.
        assertEquals(34, property(Properties.ITEM_COUNT))
        assertEquals(44f, Float.fromBits(property(Properties.ITEM_EXTENT)!!))
        assertEquals(0, property(Properties.FIRST_ITEM_INDEX))
        // Default first window (20) + overscan (2) rows = 66 cells.
        assertEquals(66, texts(frame).size)

        result.visibleRanges()[result.rootId()]!!.onVisibleRange(10, 2)
        val delta = sink.frame()
        // Window 8..13 (overscan on both sides) = 6 rows = 18 cells; keys
        // 24..41 are retained, the other 48 cells are deleted.
        assertEquals(48, delta.opcodes().count {
            it.category() == Categories.TREE && it.command() == Commands.Tree.DELETE_NODE
        })
        assertEquals(8, delta.opcodes().first {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                (it.b() and 0xffff) == Properties.FIRST_ITEM_INDEX
        }.c())
    }

    @Test
    fun lazyStackSupportsVariableItemExtents() {
        val items = (0 until 10).map { "item-$it" }
        val extents = List(10) { 40f + it }
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        emitter.mount(
            LazyVStack(spacing = 2f) {
                ForEach(items, itemExtents = extents) { item, _ -> Text(item) }
            },
            Environment.DEFAULT,
        )
        val frame = sink.frame()
        val props = frame.opcodes().filter {
            it.category() == Categories.PARAMETER && it.command() == Commands.Parameter.SET_PROPERTY
        }
        fun property(id: Int): Int? = props.firstOrNull { (it.b() and 0xffff) == id }?.c()

        assertEquals(10, property(Properties.ITEM_COUNT))
        assertEquals(44.5f, Float.fromBits(property(Properties.ITEM_EXTENT)!!))
        assertEquals(0f, Float.fromBits(property(Properties.FIRST_ITEM_OFFSET)!!))
        // sum(40..49) + 9*2 = 463.
        assertEquals(463f, Float.fromBits(property(Properties.TOTAL_EXTENT)!!))

        val offset = props.first { (it.b() and 0xffff) == Properties.ITEM_EXTENTS }.c()
        val strings = frame.strings()
        val view = ByteBuffer.wrap(strings, offset, 4).order(ByteOrder.LITTLE_ENDIAN)
        val count = view.int
        assertEquals(10, count)
        val values = FloatArray(count) { i ->
            java.lang.Float.intBitsToFloat(
                ByteBuffer.wrap(strings, offset + 4 + 4 * i, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int,
            )
        }
        assertArrayEquals(extents.toFloatArray(), values, 0.001f)
    }

    @Test
    fun lazyStackPrefetchesAheadByScrollVelocity() {
        val items = (0 until 100).map { "item-$it" }
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        val result = emitter.mount(
            LazyVStack(spacing = 0f) {
                ForEach(items) { item, _ -> Text(item).frame(height = 44f) }
            },
            Environment.DEFAULT,
        )
        sink.frame()
        val ranges = result.visibleRanges()[result.rootId()]!!
        // The first report uses the base margin.
        ranges.onVisibleRange(0, 5)
        sink.frame()
        // A fast jump derives a bounded, directional prefetch (base 2 + extra 22).
        Thread.sleep(2)
        ranges.onVisibleRange(50, 5)
        val frame = sink.frame()
        assertEquals(
            48,
            frame.opcodes().first {
                it.category() == Categories.PARAMETER &&
                    it.command() == Commands.Parameter.SET_PROPERTY &&
                    (it.b() and 0xffff) == Properties.FIRST_ITEM_INDEX
            }.c(),
        )
        assertEquals(31, texts(frame).size)
    }

    @Test
    fun lazyStackDecaysAfterAFinalReport() {
        val items = (0 until 100).map { "item-$it" }
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        val result = emitter.mount(
            LazyVStack(spacing = 0f) {
                ForEach(items) { item, _ -> Text(item).frame(height = 44f) }
            },
            Environment.DEFAULT,
        )
        sink.frame()
        val ranges = result.visibleRanges()[result.rootId()]!!
        ranges.onVisibleRange(50, 5)
        sink.frame()
        Thread.sleep(2)
        ranges.onVisibleRange(60, 5)
        sink.frame()
        // The same range again is the renderer's final report: decay to base.
        Thread.sleep(2)
        ranges.onVisibleRange(60, 5)
        val frame = sink.frame()
        // The window start stays at 58; only the prefetched tail (67..88) is
        // released, so the frame carries 22 deletes and no FIRST_ITEM_INDEX.
        assertEquals(
            22,
            frame.opcodes().count {
                it.category() == Categories.TREE &&
                    it.command() == Commands.Tree.DELETE_NODE
            },
        )
    }

    @Test
    fun lazyRowStateIsScopedByItemKeyAndSurvivesEviction() {
        class Row : Component() {
            var taps by state(0)

            override fun body(): View = Button("tap $taps") { taps++ }
        }

        val items = (0 until 100).map { "item-$it" }
        val store = InMemoryStateStore()
        val session = PersistentState(store, "s")
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        val result = emitter.mount(
            LazyVStack(spacing = 0f) {
                ForEach(items, key = { it }) { _, _ -> Row().frame(height = 44f) }
            },
            Environment(session),
        )
        assertEquals("tap 0", texts(sink.frame()).first())

        result.tapActions().values.first().run()
        assertEquals("tap 1", texts(sink.frame()).first())

        // Evict row 0, then scroll back: the recreated row restores its own
        // keyed state, and row 1 remains independent.
        result.visibleRanges()[result.rootId()]!!.onVisibleRange(50, 5)
        sink.frame()
        result.visibleRanges()[result.rootId()]!!.onVisibleRange(0, 5)
        val back = sink.frame()
        assertEquals(listOf("tap 1", "tap 0"), texts(back).take(2))
        session.close()

        // A new session over the same store reloads the keyed row state.
        val restored = PersistentState(store, "s")
        val (_, restoredFrame) = mount(
            LazyVStack(spacing = 0f) {
                ForEach(items, key = { it }) { _, _ -> Row().frame(height = 44f) }
            },
            Environment(restored),
        )
        assertEquals("tap 1", texts(restoredFrame).first())
        restored.close()
    }

    @Test
    fun twoWayTextFieldWritesBackIntoTheBoundSignal() {
        val name = signal("")
        val (result, _) = mount(TextField("Name", name))
        assertEquals(1, result.textInputs().size)
        result.textInputs().values.single().accept("Ada")
        assertEquals("Ada", name.value)
    }

    @Test
    fun twoWaySliderWritesBackIntoTheBoundSignal() {
        val value = signal(0.2f)
        val (result, _) = mount(Slider(value, 0f..1f))
        assertEquals(1, result.valueInputs().size)
        result.valueInputs().values.single().accept(0.6f)
        assertEquals(0.6f, value.value)
    }

    @Test
    fun twoWayStepperWritesBackIntoTheBoundSignal() {
        val value = signal(1f)
        val (result, _) = mount(Stepper(value, 0f..10f, 1f))
        assertEquals(1, result.valueInputs().size)
        result.valueInputs().values.single().accept(4f)
        assertEquals(4f, value.value)
    }

    @Test
    fun navigationBuildsAndSwapsDestinations() {
        val router = navigator("/") {
            route("/", Text("home"))
            route("/next") { Text("next") }
            fallback(Text("missing"))
        }
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        emitter.mount(NavigationStack(router), Environment.DEFAULT)

        assertEquals("/", router.path())
        assertEquals(listOf("home"), texts(sink.frame()))

        router.push("/next")
        assertEquals("/next", router.path())
        assertEquals(listOf("next"), texts(sink.frame()))
    }

    @Test
    fun navigationLinkUsesTheNearestRouter() {
        val router = navigator("/") {
            route("/") {
                VStack {
                    Text("home")
                    NavigationLink("go", to = "/next")
                }
            }
            route("/next") { Text("next") }
            fallback(Text("missing"))
        }
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        val result = emitter.mount(NavigationStack(router), Environment.DEFAULT)

        assertEquals(1, result.navigateActions().size, "the link resolves to the enclosing router")
        result.navigateActions().values.single().run()
        assertEquals("/next", router.path())
        assertEquals(listOf("next"), texts(sink.frame()))
    }

    @Test
    fun fieldSubviewDoesNotLeakIntoTheParentScope() {
        class Child : Component() {
            override fun body(): View = Text("child")
        }
        class Parent : Component() {
            private val child = Child()
            override fun body(): View = VStack {
                Text("parent")
                +child
            }
        }

        val (_, frame) = mount(VStack { Parent() })
        assertEquals(listOf("parent", "child"), texts(frame), "the field child renders once, inside the parent")
    }

    @Test
    fun secureFieldDeclaresIsSecure() {
        val (_, frame) = mount(SecureField("Password", signal("")))
        assertTrue(properties(frame).contains(Properties.IS_SECURE), "SecureField emits IS_SECURE")
    }


    @Test
    fun onFocusChangedDeclaresTheListenerAndRoutesReports() {
        var gained: Boolean? = null
        val (result, frame) = mount(Text("row").onFocusChanged { gained = it })
        val id = result.rootId()
        val listeners = frame.opcodes().first {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                it.a() == id && (it.b() and 0xffff) == Properties.EVENT_LISTENERS
        }.c()
        assertTrue(listeners and Commands.Listeners.FOCUS != 0)
        assertTrue(result.focusInputs().containsKey(id))

        result.focusInputs()[id]!!.accept(true)
        assertEquals(true, gained)
        result.focusInputs()[id]!!.accept(false)
        assertEquals(false, gained)
    }

    @Test
    fun onKeyDownDeclaresTheListenerAndRoutesCanonicalKeys() {
        var received: KeyEvent? = null
        val (result, frame) = mount(Text("row").onKeyDown { received = it })
        val id = result.rootId()
        val listeners = frame.opcodes().first {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                it.a() == id && (it.b() and 0xffff) == Properties.EVENT_LISTENERS
        }.c()
        assertTrue(listeners and Commands.Listeners.KEY_DOWN != 0)

        InputDispatcher(result, signal("/")).dispatch(Event.keyDown(id, Keys.ARROW_DOWN, 0, 0))
        assertEquals(Keys.ARROW_DOWN, received?.keyCode)
        assertEquals(false, received?.repeat)
    }

    @Test
    fun focusedEmitsSetFocusForTheTargetNode() {
        val focus = signal(false)
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        val result = emitter.mount(
            VStack {
                Text("a").focusable().focused(focus)
                Text("b")
            },
            Environment.DEFAULT,
        )
        assertTrue(sink.frame().opcodes().none {
            it.category() == Categories.META && it.command() == Commands.Meta.SET_FOCUS
        })

        focus.value = true
        val delta = sink.frame()
        val focusOp = delta.opcodes().single {
            it.category() == Categories.META && it.command() == Commands.Meta.SET_FOCUS
        }
        assertEquals(1, focusOp.b())
        assertTrue(focusOp.a() != 0 && focusOp.a() != result.rootId())
    }

    @Test
    fun lazyStackPinsAFarIndexIntoTheEmittedWindow() {
        val items = signal((0 until 100).map { "item-$it" })
        val pin = signal<Int?>(null)
        val sink = FrameOpcodeSink()
        val emitter = Emitter(sink)
        emitter.mount(
            LazyVStack(spacing = 0f, pin = pin) {
                ForEach(items) { item, _ -> Text(item).frame(height = 44f) }
            },
            Environment.DEFAULT,
        )

        pin.value = 50
        val pinned = sink.frame()
        assertTrue(texts(pinned).contains("item-50"), "the pinned row is emitted")
        assertTrue(texts(pinned).contains("item-22"), "the contiguous union keeps the visible window")

        pin.value = null
        val unpinned = sink.frame()
        assertTrue(texts(unpinned).none { it == "item-50" }, "clearing the pin returns to the visible window")
    }

    @Test
    fun sliderStepEmitsStepValue() {
        val value = signal(0.5f)
        val (_, frame) = mount(Slider(value, 0f..1f, step = 0.05f))
        val step = frame.opcodes().first {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                (it.b() and 0xffff) == Properties.STEP_VALUE
        }
        assertEquals(0.05f, Float.fromBits(step.c()))
    }
}
