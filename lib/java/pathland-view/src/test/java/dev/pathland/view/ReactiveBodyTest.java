package dev.pathland.view;

import dev.pathland.view.emit.Emitter;
import dev.pathland.view.emit.FrameOpcodeSink;
import dev.pathland.view.emit.Opcode;
import dev.pathland.view.emit.ProtocolFrame;
import dev.pathland.view.signal.Signals;
import dev.pathland.view.signal.WritableSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-component reactive bodies (#88): a signal read in a component's {@code body()}
 * re-renders that component; the retained-tree diff still emits only actual changes.
 */
class ReactiveBodyTest {

    private static long countOps(ProtocolFrame frame, int category, int command) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == category && o.command() == command)
                .count();
    }

    private static boolean containsText(ProtocolFrame frame, String text) {
        return frame.opcodes().stream().anyMatch(o ->
                o.category() == Categories.PARAMETER
                        && o.command() == Commands.Parameter.SET_TEXT
                        && text.equals(frame.stringAt(o.b())));
    }

    private static String lastText(ProtocolFrame frame) {
        Opcode text = frame.opcodes().stream()
                .filter(o -> o.category() == Categories.PARAMETER
                        && o.command() == Commands.Parameter.SET_TEXT)
                .reduce((a, b) -> b)
                .orElseThrow();
        return frame.stringAt(text.b());
    }

    @Test
    void bareIfInBodyReRendersThatComponentOnly() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Boolean> show = Signals.signal(true);

        View toggle = new View() {
            @Override
            public View body() {
                if (show.get()) {
                    return Text.make("A");
                }
                return Text.make("B");
            }
        };
        emitter.mount(VStack.children(toggle, Text.make("static")), Environment.DEFAULT);
        assertTrue(containsText(sink.frame(), "A"));

        int frames = sink.framesProduced();
        show.set(false);
        ProtocolFrame delta = sink.frame();
        assertEquals(frames + 1, sink.framesProduced(), "one re-render, one frame");
        assertEquals(1, delta.opcodes().size(), "only the changed text is emitted");
        assertEquals("B", lastText(delta));
    }

    @Test
    void bareSwitchInBody() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Integer> mode = Signals.signal(1);

        View screen = new View() {
            @Override
            public View body() {
                return switch (mode.get()) {
                    case 1 -> Text.make("Home");
                    case 2 -> Text.make("Users");
                    default -> Text.make("NotFound");
                };
            }
        };
        emitter.mount(screen, Environment.DEFAULT);
        assertEquals("Home", lastText(sink.frame()));

        mode.set(2);
        assertEquals("Users", lastText(sink.frame()));
        mode.set(9);
        assertEquals("NotFound", lastText(sink.frame()));
    }

    @Test
    void loopOverSignalListReRendersRows() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<List<String>> items = Signals.signal(List.of("a", "b"));

        View list = new View() {
            @Override
            public View body() {
                View[] rows = items.get().stream().map(Text::make).toArray(View[]::new);
                return VStack.make().children(rows);
            }
        };
        emitter.mount(list, Environment.DEFAULT);

        items.set(List.of("a", "b", "c"));
        ProtocolFrame added = sink.frame();
        assertEquals(1, countOps(added, Categories.TREE, Commands.Tree.CREATE_NODE));
        assertEquals(1, countOps(added, Categories.TREE, Commands.Tree.INSERT_CHILD));
        assertEquals("c", lastText(added));

        items.set(List.of("a", "x"));
        ProtocolFrame changed = sink.frame();
        assertEquals("x", lastText(changed));
        assertEquals(1, countOps(changed, Categories.TREE, Commands.Tree.REMOVE_CHILD));
        assertEquals(1, countOps(changed, Categories.TREE, Commands.Tree.DELETE_NODE));
    }

    @Test
    void derivedConfigValueReEmitsOnlyThatProperty() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Boolean> dense = Signals.signal(false);

        View root = new View() {
            @Override
            public View body() {
                return VStack.make(v -> v.spacing(dense.get() ? 4f : 8f));
            }
        };
        emitter.mount(root, Environment.DEFAULT);

        dense.set(true);
        ProtocolFrame delta = sink.frame();
        assertEquals(1, delta.opcodes().size(), "only SPACING changed");
        Opcode only = delta.opcodes().get(0);
        assertEquals(Categories.PARAMETER, only.category());
        assertEquals(Commands.Parameter.SET_PROPERTY, only.command());
        assertEquals(Properties.SPACING, only.b() & 0xFFFF);
    }

    @Test
    void threeTextsEmitOnlyTheChangedOne() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<String> middle = Signals.signal("B");

        View root = new View() {
            @Override
            public View body() {
                return VStack.make().children(
                        Text.make("A"),
                        Text.make(middle.get()),
                        Text.make("C"));
            }
        };
        emitter.mount(root, Environment.DEFAULT);

        middle.set("B2");
        ProtocolFrame delta = sink.frame();
        assertEquals(1, delta.opcodes().size(), "one changed text -> one SET_TEXT");
        assertEquals(Commands.Parameter.SET_TEXT, delta.opcodes().get(0).command());
        assertEquals("B2", lastText(delta));
    }

    @Test
    void childReadDoesNotReRenderTheParent() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Boolean> show = Signals.signal(true);
        AtomicInteger parentBodies = new AtomicInteger();

        View child = new View() {
            @Override
            public View body() {
                return show.get() ? Text.make("child-a") : Text.make("child-b");
            }
        };
        View parent = new View() {
            @Override
            public View body() {
                parentBodies.incrementAndGet();
                return VStack.make().children(Text.make("parent"), child);
            }
        };
        emitter.mount(parent, Environment.DEFAULT);
        assertEquals(1, parentBodies.get());

        show.set(false);
        assertEquals(1, parentBodies.get(), "the parent body is not re-run");
        assertEquals("child-b", lastText(sink.frame()));
    }

    @Test
    void boundSignalDoesNotReRenderTheComponent() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<String> label = Signals.signal("one");
        AtomicInteger bodies = new AtomicInteger();

        View root = new View() {
            @Override
            public View body() {
                bodies.incrementAndGet();
                return Text.make(t -> t.text(label));
            }
        };
        emitter.mount(root, Environment.DEFAULT);
        assertEquals(1, bodies.get());

        label.set("two");
        assertEquals(1, bodies.get(), "a bound signal does not re-run the body");
        assertEquals("two", lastText(sink.frame()));
    }

    @Test
    void changingTheRootComponentTypeFailsLoudly() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Boolean> text = Signals.signal(true);

        View root = new View() {
            @Override
            public View body() {
                if (text.get()) {
                    return Text.make("a");
                }
                return Button.make(b -> b.title("b"));
            }
        };
        emitter.mount(root, Environment.DEFAULT);

        RuntimeException error = assertThrows(RuntimeException.class, () -> text.set(false));
        Throwable cause = error.getCause() == null ? error : error.getCause();
        assertTrue(cause.getMessage().contains("stable"), cause.getMessage());
    }

    @Test
    void nonSignalReadIsNotReactive() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        AtomicInteger value = new AtomicInteger(1);
        AtomicInteger bodies = new AtomicInteger();

        View root = new View() {
            @Override
            public View body() {
                bodies.incrementAndGet();
                return Text.make("v" + value.get());
            }
        };
        emitter.mount(root, Environment.DEFAULT);
        int frames = sink.framesProduced();

        value.set(2); // plain field: no signal, no re-render
        assertEquals(frames, sink.framesProduced());
        assertEquals(1, bodies.get());
    }

    @Test
    void modifierIsReappliedWhenTheComponentReRenders() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Boolean> show = Signals.signal(true);

        View root = new View() {
            @Override
            public View body() {
                View text = show.get() ? Text.make("a") : Text.make("b");
                return text.modifiers(Modifiers.padding(p -> p.uniform(4)));
            }
        };
        emitter.mount(root, Environment.DEFAULT);
        assertTrue(hasPadding(sink.frame()));

        show.set(false);
        sink.frame(); // the SET_TEXT delta
        emitter.renderFull(); // re-emits the retained tree
        assertTrue(hasPadding(sink.frame()), "the modifier survives the re-render");
    }

    private static boolean hasPadding(ProtocolFrame frame) {
        return frame.opcodes().stream().anyMatch(o ->
                o.category() == Categories.PARAMETER
                        && o.command() == Commands.Parameter.SET_PROPERTY
                        && (o.b() & 0xFFFF) == Properties.PADDING);
    }

    @Test
    void environmentScopeIsReappliedOnReRender() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Boolean> show = Signals.signal(true);
        EnvironmentKey<String> key = EnvironmentKey.of("test.scope");

        View child = new View() {
            @Override
            public View body() {
                String value = Environment.value(key).get();
                return Text.make((show.get() ? "a:" : "b:") + value);
            }
        };
        View root = VStack.children(child)
                .modifiers(Modifiers.environmentBinding(e -> e.key(key).value("scoped")));
        emitter.mount(root, Environment.DEFAULT);
        assertEquals("a:scoped", lastText(sink.frame()));

        show.set(false);
        assertEquals("b:scoped", lastText(sink.frame()), "the captured scope is re-applied");
    }
}
