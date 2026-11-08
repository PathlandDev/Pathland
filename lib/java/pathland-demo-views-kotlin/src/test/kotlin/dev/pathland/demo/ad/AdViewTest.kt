package dev.pathland.demo.ad

import dev.pathland.view.core.Environment
import dev.pathland.core.protocol.Categories
import dev.pathland.core.protocol.Commands
import dev.pathland.core.protocol.Components
import dev.pathland.core.protocol.Opcode
import dev.pathland.view.core.state.InMemoryStateStore
import dev.pathland.view.core.state.PersistentState
import dev.pathland.core.transport.FrameOpcodeSink
import dev.pathland.core.transport.ProtocolFrame
import dev.pathland.view.engine.Emitter
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The small ad app — a title and a button that reacts to clicks — the origin of
 * the music player's sponsored proxy slot (spec/PROXY.md).
 */
class AdViewTest {

    @Test
    fun buttonClickIncrementsTheCounter() {
        val state = PersistentState(InMemoryStateStore(), "session-1")
        val sink = FrameOpcodeSink()
        val result = Emitter(sink).mount(AdView(), Environment(state))

        assertTrue(anySetText(sink.frame(), "Pathland Ads"), "the title renders")
        assertTrue(anySetText(sink.frame(), "Clicked 0 times"), "the counter starts at zero")

        val buttonId = sink.frame().opcodes().stream()
            .filter { it.category() == Categories.TREE && it.command() == Commands.Tree.CREATE_NODE }
            .filter { (it.b() and 0xFFFF) == Components.BUTTON }
            .mapToInt { it.a() }
            .findFirst()
            .orElseThrow { AssertionError("no button node") }

        // The host routes a tap into its action; the action writes the counter
        // state, and the emitter re-emits only its text.
        result.tapActions()[buttonId]!!.run()

        assertTrue(anySetText(sink.frame(), "Clicked 1 time"), "the click increments the counter")
    }

    private fun anySetText(frame: ProtocolFrame, text: String): Boolean =
        frame.opcodes().any {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_TEXT &&
                text == frame.stringAt(it.b())
        }
}
