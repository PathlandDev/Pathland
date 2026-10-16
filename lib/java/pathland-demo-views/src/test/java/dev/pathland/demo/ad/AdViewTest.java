package dev.pathland.demo.ad;

import dev.pathland.view.Categories;
import dev.pathland.view.Commands;
import dev.pathland.view.Components;
import dev.pathland.view.Environment;
import dev.pathland.view.emit.Emitter;
import dev.pathland.view.emit.FrameOpcodeSink;
import dev.pathland.view.emit.Opcode;
import dev.pathland.view.emit.ProtocolFrame;
import dev.pathland.view.emit.RenderResult;
import dev.pathland.view.state.InMemoryStateStore;
import dev.pathland.view.state.PersistentState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The small ad app — a title and a button that reacts to clicks — the origin of
 * the music player's sponsored proxy slot (spec/PROXY.md).
 */
class AdViewTest {

    @Test
    void buttonClickIncrementsTheCounter() {
        PersistentState state = new PersistentState(new InMemoryStateStore(), "session-1");
        FrameOpcodeSink sink = new FrameOpcodeSink();
        RenderResult result = new Emitter(sink).mount(new AdView(), new Environment(state));

        assertTrue(anySetText(sink.frame(), "Pathland Ads"), "the title renders");
        assertTrue(anySetText(sink.frame(), "Clicked 0 times"), "the counter starts at zero");

        int buttonId = sink.frame().opcodes().stream()
                .filter(o -> o.category() == Categories.TREE && o.command() == Commands.Tree.CREATE_NODE)
                .filter(o -> (o.b() & 0xFFFF) == Components.BUTTON)
                .mapToInt(Opcode::a)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no button node"));

        // The host routes a tap (POINTER_UP on the button) into its action; the
        // action writes the counter state, and the emitter re-emits only its text.
        result.tapActions().get(buttonId).run();

        assertTrue(anySetText(sink.frame(), "Clicked 1 time"), "the click increments the counter");
    }

    private static boolean anySetText(ProtocolFrame frame, String text) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.PARAMETER && op.command() == Commands.Parameter.SET_TEXT
                    && text.equals(frame.stringAt(op.b()))) {
                return true;
            }
        }
        return false;
    }
}
