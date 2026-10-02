package com.pathland.demo.tui;

import com.pathland.demo.DemoTheme;
import com.pathland.demo.music.MusicPlayerView;
import com.pathland.view.Environment;
import com.pathland.view.emit.Emitter;
import com.pathland.view.emit.InputDispatcher;
import com.pathland.view.emit.RenderResult;
import com.pathland.view.ffm.PathlandCore;
import com.pathland.view.ffm.RingOpcodeSink;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;
import com.pathland.view.state.InMemoryStateStore;
import com.pathland.view.state.PersistentState;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;

/**
 * Runs the shared {@link MusicPlayerView} under the native Ratatui renderer —
 * the cross-language, shared-renderer story for Java, in the terminal.
 *
 * <p>The Java DSL mounts the view through its fine-grained {@link Emitter} into a
 * {@code libpathland_core} shared ring ({@link RingOpcodeSink}, zero-copy). The
 * in-process TUI renderer pumps the same ring in place
 * ({@code pathland_tui_run_ring}); terminal inputs (mouse + keyboard) round-trip
 * as {@code EVENT} opcodes that this host drains and dispatches through
 * {@link InputDispatcher} into the app's bindings (taps, value inputs, router
 * back). The renderer is the wire — no Swing, no GTK, only Pathland authoring.
 *
 * <p>Audio playback and album covers have no terminal equivalent yet: the
 * {@code AUDIO}/{@code IMAGE} nodes render as placeholders, and the scrollable
 * track list shows its first child (scrolling is a renderer follow-up). The
 * player's controls (play/pause/next/prev, volume + seek sliders) work with the
 * mouse or Tab + arrows; quit with {@code q} or {@code Ctrl+C}.
 *
 * <p>Run (one-time: build the Rust dylibs, then the Java reactor):
 * <pre>{@code
 *   cargo build -p pathland-core-capi -p pathland-render-tui
 *   cd lib/java && mvn -q install
 *   cd pathland-tui-demo
 *   mvn -q compile exec:java \
 *     -Dpathland.core.lib=$PWD/../../lib/rust/target/debug/libpathland_core.dylib \
 *     -Dpathland.tui.lib=$PWD/../../lib/rust/target/debug/libpathland_render_tui.dylib
 * }</pre>
 */
public final class TuiHost {

    /** Max events drained per wake (× 16 bytes each). */
    private static final int MAX_EVENTS = 64;

    public static void main(String[] args) {
        PathlandCore core = PathlandCore.instance();
        try (RingOpcodeSink sink = new RingOpcodeSink(core)) {
            Pointer handle = sink.handle();

            // The session's state + theme, exactly as the server session seeds them.
            PersistentState state = new PersistentState(new InMemoryStateStore(), "tui");
            // A placeholder host path signal: MusicPlayerView has no router, so the
            // dispatcher's NAVIGATE handling is never exercised with a URL.
            WritableSignal<String> activePath = Signals.signal("/");

            Emitter emitter = new Emitter(sink, DemoTheme.adaptive());
            RenderResult result = emitter.mount(
                    new MusicPlayerView(),
                    new Environment(state));
            InputDispatcher dispatcher = new InputDispatcher(result, activePath);
            RingEventReader reader =
                    new RingEventReader(core.ringPtr(handle), core.ringLen(handle));

            // Wake callback: drain raw EVENT opcodes from the shared ring and route
            // them into the app's bindings. Re-emission (signal → emitter → ring)
            // happens on this thread after the wake, the only writer.
            TuiRendererNative.EventCallback onEvent = () -> {
                Memory buf = new Memory((long) MAX_EVENTS * 16);
                int n = core.drainEvents(handle, buf, MAX_EVENTS);
                if (n > 0) {
                    byte[] raw = buf.getByteArray(0, n * 16);
                    dispatcher.dispatch(reader.decode(raw));
                }
            };

            // Blocks until the user quits (q / Ctrl+C); the ring (handle) must
            // outlive it.
            TuiRendererNative renderer = TuiRendererNative.instance();
            renderer.runRing(core.ringMut(handle), onEvent);
        }
    }
}