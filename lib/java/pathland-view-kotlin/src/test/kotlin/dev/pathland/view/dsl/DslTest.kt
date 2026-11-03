package dev.pathland.view.dsl

import dev.pathland.view.core.protocol.Categories
import dev.pathland.view.core.protocol.Commands
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
    fun kotlinTreeEmitsTheSameBytesAsJava() {
        val kotlinRoot = VStack(spacing = 8f) {
            Text("Hello")
            Button("Tap") {}
        }
        val javaRoot = dev.pathland.view.VStack.make { c -> c.spacing(8f) }.children(
            dev.pathland.view.Text.make("Hello"),
            dev.pathland.view.Button.make { c -> c.title("Tap").action {} },
        )

        val (_, kotlinFrame) = mount(kotlinRoot)
        val (_, javaFrame) = mount(javaRoot)

        assertEquals(javaFrame.opcodes(), kotlinFrame.opcodes(), "Kotlin builders must emit identical opcodes")
        assertArrayEquals(javaFrame.strings(), kotlinFrame.strings(), "arena strings must match")
    }

    @Test
    fun coreOwnedControlsEmitTheSameBytesAsJava() {
        val name = signal("")
        val on = signal(true)
        val value = signal(0.5f)
        val color = signal(dev.pathland.view.core.Color.RED)

        val kotlinRoot = VStack {
            TextField("Name", name)
            Toggle("On", on)
            Slider(value, 0f..1f)
            ColorPicker(color)
        }
        val javaRoot = dev.pathland.view.VStack.children(
            dev.pathland.view.TextField.make { c -> c.placeholder("Name").text(name) },
            dev.pathland.view.Toggle.make { c -> c.label("On").isOn(on) },
            dev.pathland.view.Slider.make { c -> c.value(value).`in`(0f, 1f) },
            dev.pathland.view.ColorPicker.make { c -> c.selection(color) },
        )

        val (_, kotlinFrame) = mount(kotlinRoot)
        val (_, javaFrame) = mount(javaRoot)
        assertEquals(javaFrame.opcodes(), kotlinFrame.opcodes())
        assertArrayEquals(javaFrame.strings(), kotlinFrame.strings())
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
        class Greeting : KotlinView() {
            override fun body(): View = Text("hi")
        }

        val (_, frame) = mount(VStack { Greeting() })
        assertEquals(listOf("hi"), texts(frame))
    }

    @Test
    fun modifierSugarEmitsTheSamePropertiesAsTheModifiersFacade() {
        val sugared = Text("x").padding(16f).frame(width = 100f, height = 20f)
        val explicit = dev.pathland.view.Text.make("x").modifiers(
            dev.pathland.view.Modifiers.padding(16f),
            dev.pathland.view.Modifiers.frame { c -> c.width(100f).height(20f) },
        )

        val (_, sugaredFrame) = mount(sugared)
        val (_, explicitFrame) = mount(explicit)
        assertEquals(explicitFrame.opcodes(), sugaredFrame.opcodes())
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
    fun compositesEmitTheSameBytesAsJava() {
        val kotlinRoot = VStack {
            Label("Home", "home")
            ViewThatFits(Fit(Text("a")), Fit(Text("b"), 640f))
            ProxyView("https://origin.example", "/music", "svc")
        }
        val javaRoot = dev.pathland.view.VStack.children(
            dev.pathland.view.Label.make { c -> c.title("Home").icon("home") },
            dev.pathland.view.SizeThatFits.make(
                dev.pathland.view.Fit.of(dev.pathland.view.Text.make("a")),
                dev.pathland.view.Fit.of(dev.pathland.view.Text.make("b"), 640f),
            ),
            dev.pathland.view.ProxyView.make { c ->
                c.endpoint("https://origin.example").route("/music").credential("svc")
            },
        )

        val (_, kotlinFrame) = mount(kotlinRoot)
        val (_, javaFrame) = mount(javaRoot)
        assertEquals(javaFrame.opcodes(), kotlinFrame.opcodes())
        assertArrayEquals(javaFrame.strings(), kotlinFrame.strings())
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
    fun stateDelegateIsPersistedAndReactive() {
        class Counter : KotlinView() {
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
        class Child : KotlinView() {
            override fun body(): View = Text("child")
        }
        class Parent : KotlinView() {
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
