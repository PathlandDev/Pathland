package com.pathland.demo;

import com.pathland.view.Categories;
import com.pathland.view.Commands;
import com.pathland.view.Components;
import com.pathland.view.Environment;
import com.pathland.view.Platform;
import com.pathland.view.Properties;
import com.pathland.view.emit.Emitter;
import com.pathland.view.emit.Frame;
import com.pathland.view.emit.FrameOpcodeSink;
import com.pathland.view.emit.Opcode;
import com.pathland.view.emit.RenderResult;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;
import com.pathland.view.state.InMemoryStateStore;
import com.pathland.view.state.PersistentState;
import com.pathland.view.state.StateStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The split navigation demo (spec PRIMITIVES.md — `NavigationSplitView` → `HStack`
 * sidebar + detail): a fixed menu column on the left with three items and a
 * `NavigationContainer` content area on the right. The host provides the active path
 * ({@code Platform.ACTIVE_PATH}); {@code SplitNavDemo.body()} builds a **bound** router
 * from it. Menu clicks navigate (direct selection — no back-stack growth), the
 * `/kitchen` content is the full `KitchenSinkView`, and the active menu row is
 * highlighted reactively.
 */
class SplitNavDemoTest {

    private static final int ACTIVE_BG = 0xFF_DCE4FF; // SplitNavDemo.ACTIVE_BG

    /** A session environment so the kitchen-sink content's `State` fields wire up. */
    private static Environment env() {
        StateStore store = new InMemoryStateStore();
        return new Environment(new PersistentState(store, "session-1"));
    }

    /** The host pattern: provide the active path, mount the demo root. */
    private static Mount mount(FrameOpcodeSink sink, String initialPath) {
        WritableSignal<String> activePath = Signals.signal(initialPath);
        RenderResult result = new Emitter(sink).mount(
                new SplitNavDemo().environment(Platform.ACTIVE_PATH, activePath), env());
        return new Mount(result, activePath);
    }

    private record Mount(RenderResult result, WritableSignal<String> activePath) {}

    @Test
    void hostSeedRendersTheSidebarAndTheContent() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        mount(sink, "/home");

        Frame frame = sink.frame();
        assertTrue(anySetText(frame, "Home"), "the home menu row is rendered");
        assertTrue(anySetText(frame, "Kitchen sink"), "the kitchen sink menu row is rendered");
        assertTrue(anySetText(frame, "Settings"), "the settings menu row is rendered");
        assertTrue(anyCreateNode(frame, Components.IMAGE), "the menu rows render their Label icons");
        assertTrue(anySetPropertyString(frame, Properties.LABEL, "Home"),
                "a menu row carries its title as the accessibility label");
        assertTrue(anySetText(frame,
                "A master-detail (split) navigation demo: the menu on the left drives the content area on the right."),
                "the home content area rendered on the right");
        assertEquals("/home", routeOf(frame));
    }

    @Test
    void kitchenSinkRendersAsAContentArea() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        mount(sink, "/kitchen");

        Frame frame = sink.frame();
        assertTrue(anySetText(frame, "Pathland Kitchensink"),
                "the /kitchen content area is the full KitchenSinkView");
        assertEquals("/kitchen", routeOf(frame));
    }

    @Test
    void declarativeNavButtonInsideAContentAreaChangesTheRoute() {
        // HomeView's "Open kitchen sink" button declares `.navigate("/kitchen")` — a
        // component *inside* the NavigationContainer, so the emitter resolves it to the
        // nearest enclosing router and registers it in navigateActions (spec DSL.md §4.5).
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Mount mount = mount(sink, "/home");

        assertTrue(mount.result().navigateActions().size() >= 1,
                "the declarative button inside the content area registers a nav intent");
        mount.result().navigateActions().values().iterator().next().run();
        assertEquals("/kitchen", mount.activePath().get(),
                "the declarative .navigate button changed the route (mirrored into the active path)");
        assertTrue(anySetText(sink.frame(), "Pathland Kitchensink"),
                "the content area swapped to the kitchen sink");
    }

    @Test
    void menuClickSwapsTheContentArea() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Mount mount = mount(sink, "/home");

        // The menu rows capture the router explicitly (the sidebar sits outside the
        // NavigationContainer), so they navigate via the tap registry — clicking them in
        // order reaches /kitchen on the second row. Snapshot the live map first (a tap
        // re-renders and repopulates it).
        boolean reachedKitchen = false;
        for (Map.Entry<Integer, Runnable> tap : new ArrayList<>(mount.result().tapActions().entrySet())) {
            tap.getValue().run();
            if ("/kitchen".equals(mount.activePath().get())) {
                reachedKitchen = true;
                break;
            }
        }
        assertTrue(reachedKitchen, "clicking the Kitchen sink menu row navigates the content area");
        Frame delta = sink.frame();
        assertTrue(anySetText(delta, "Pathland Kitchensink"),
                "the kitchen sink content swap is emitted as deltas");
        assertEquals("/kitchen", routeOf(delta));
    }

    @Test
    void activeRowHighlightTracksTheRoute() {
        RecordingSink sink = new RecordingSink();
        Mount mount = mount(sink, "/home");

        // Seeded at /home: the Home row carries the active background color.
        assertTrue(anyFrame(sink, f -> anySetPropertyBits(f, Properties.BACKGROUND_COLOR, ACTIVE_BG)),
                "the active menu row emits the highlight color");

        // An external path change (the host setting Platform.ACTIVE_PATH, e.g. a deep
        // link) re-routes the bound router; the row's reactive background re-emits.
        // A single flush can produce several frames (the swap + each binding effect),
        // so capture them all.
        sink.frames.clear();
        mount.activePath().set("/settings");
        assertTrue(anyFrame(sink, f -> anySetPropertyBits(f, Properties.BACKGROUND_COLOR, ACTIVE_BG)),
                "the highlight re-emits for the newly active row");
        assertTrue(anyFrame(sink, f -> "/settings".equals(routeOfOrNull(f))),
                "the external path change re-routed the content area");
    }

    /** A sink that records every completed frame (a flush can emit several). */
    private static final class RecordingSink extends FrameOpcodeSink {
        final List<Frame> frames = new ArrayList<>();

        @Override
        public void endFrame() {
            super.endFrame();
            frames.add(frame());
        }
    }

    private static boolean anyFrame(RecordingSink sink, java.util.function.Predicate<Frame> test) {
        return sink.frames.stream().anyMatch(test);
    }

    private static String routeOf(Frame frame) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_PROPERTY
                    && (op.b() & 0xFFFF) == Properties.ROUTE) {
                return frame.stringAt(op.c());
            }
        }
        throw new AssertionError("no ROUTE property in frame");
    }

    /** The ROUTE path in a frame, or null when the frame carries no ROUTE. */
    private static String routeOfOrNull(Frame frame) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_PROPERTY
                    && (op.b() & 0xFFFF) == Properties.ROUTE) {
                return frame.stringAt(op.c());
            }
        }
        return null;
    }

    private static boolean anySetText(Frame frame, String text) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_TEXT
                    && text.equals(frame.stringAt(op.b()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyCreateNode(Frame frame, int component) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.TREE && op.command() == Commands.Tree.CREATE_NODE
                    && op.b() == component) {
                return true;
            }
        }
        return false;
    }

    private static boolean anySetPropertyString(Frame frame, int property, String value) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_PROPERTY
                    && (op.b() & 0xFFFF) == property
                    && value.equals(frame.stringAt(op.c()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean anySetPropertyBits(Frame frame, int property, int bits) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_PROPERTY
                    && (op.b() & 0xFFFF) == property && (int) op.c() == bits) {
                return true;
            }
        }
        return false;
    }
}