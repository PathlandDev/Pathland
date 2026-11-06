package dev.pathland.view.dsl

import dev.pathland.view.core.AttributedString
import dev.pathland.view.core.protocol.Categories
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.core.protocol.Components
import dev.pathland.view.core.Environment
import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.core.View
import dev.pathland.view.engine.Emitter
import dev.pathland.view.core.transport.FrameOpcodeSink
import dev.pathland.view.core.protocol.Opcode
import dev.pathland.view.core.transport.ProtocolFrame
import dev.pathland.view.engine.RenderResult
import dev.pathland.view.core.state.InMemoryStateStore
import dev.pathland.view.core.state.PersistentState
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
        assertTrue(created.contains(Components.SIZE_THAT_FITS))
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

}
