package dev.pathland.demo.counter;

import dev.pathland.view.core.state.State;

import dev.pathland.view.core.protocol.Categories;
import dev.pathland.view.core.protocol.Commands;
import dev.pathland.view.core.Environment;
import dev.pathland.view.engine.Emitter;
import dev.pathland.view.core.transport.ProtocolFrame;
import dev.pathland.view.core.transport.FrameOpcodeSink;
import dev.pathland.view.core.protocol.Opcode;
import dev.pathland.view.engine.RenderResult;
import dev.pathland.view.core.state.InMemoryStateStore;
import dev.pathland.view.core.state.PersistentState;
import dev.pathland.view.core.state.StateStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end: the generated binder wires State fields into the store. */
class CounterViewTest {

    @Test
    void wiredStateLoadsPersistedValues() {
        StateStore store = new InMemoryStateStore();
        store.save("count:session-1", 5);
        store.save("name:session-1", "Ada");

        PersistentState state = new PersistentState(store, "session-1");
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);

        emitter.mount(new CounterView(), new Environment(state));

        ProtocolFrame frame = sink.frame();
        assertTrue(anySetText(frame, "Count: 5"), "count label reflects the persisted value");
        assertTrue(anySetText(frame, "Name: Ada"), "name label reflects the persisted value");

        state.close();
        emitter.destroy();
    }

    @Test
    void mutatingWiredStatePersistsIt() {
        StateStore store = new InMemoryStateStore();
        PersistentState state = new PersistentState(store, "session-1");
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);

        CounterView root = new CounterView();
        RenderResult result = emitter.mount(root, new Environment(state));

        // Drive the edit through the emitter's input registry (exactly as the host does),
        // not by reaching into a view's signal.
        result.textInputs().values().iterator().next().accept("Bob");
        assertEquals(Optional.of("Bob"), store.load("name:session-1", String.class));

        state.close();
        emitter.destroy();
    }

    private static boolean anySetText(ProtocolFrame frame, String text) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.PARAMETER && op.command() == Commands.Parameter.SET_TEXT) {
                if (text.equals(frame.stringAt(op.b()))) {
                    return true;
                }
            }
        }
        return false;
    }
}