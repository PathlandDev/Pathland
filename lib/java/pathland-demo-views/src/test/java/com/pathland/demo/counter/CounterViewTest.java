package com.pathland.demo.counter;

import com.pathland.view.Categories;
import com.pathland.view.Commands;
import com.pathland.view.Environment;
import com.pathland.view.emit.Emitter;
import com.pathland.view.emit.Frame;
import com.pathland.view.emit.FrameOpcodeSink;
import com.pathland.view.emit.Opcode;
import com.pathland.view.emit.RenderResult;
import com.pathland.view.state.InMemoryStateStore;
import com.pathland.view.state.PersistentState;
import com.pathland.view.state.StateStore;
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

        Frame frame = sink.frame();
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

    private static boolean anySetText(Frame frame, String text) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_TEXT) {
                if (text.equals(frame.stringAt(op.b()))) {
                    return true;
                }
            }
        }
        return false;
    }
}