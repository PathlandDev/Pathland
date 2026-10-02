package com.pathland.demo.gtk;

import com.pathland.demo.DemoTheme;
import com.pathland.demo.assets.DemoAssets;
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
 * Runs the shared {@link MusicPlayerView} under the native GTK4 renderer — the
 * cross-language, shared-renderer story for Java.
 *
 * <p>The Java DSL mounts the view through its fine-grained {@link Emitter} into a
 * {@code libpathland_core} shared ring ({@link RingOpcodeSink}, zero-copy). The
 * in-process GTK renderer pumps the same ring in place
 * ({@code pathland_gtk_run_ring}); native inputs round-trip as {@code EVENT}
 * opcodes that this host drains and dispatches through {@link InputDispatcher}
 * into the app's bindings (taps, value/date inputs, media events, router back).
 * App-driven media (spec/EVENTS.md Media) plays through the renderer's native
 * stream (GTK's GStreamer-backed media): the demo's tracks + covers are
 * extracted from the classpath and served as the asset root, so the app's
 * web-style {@code /_pathland/assets/...} paths resolve locally.
 *
 * <p>No Swing, no GTK API — only Pathland authoring.
 *
 * <p>Run (one-time: build the Rust dylibs, then the Java reactor):
 * <pre>{@code
 *   cargo build -p pathland-core-capi -p pathland-render-gtk
 *   cd lib/java && mvn -q install
 *   cd pathland-gtk-demo
 *   MAVEN_OPTS="-XstartOnFirstThread" mvn -q compile exec:java \
 *     -Dpathland.core.lib=$PWD/../../lib/rust/target/debug/libpathland_core.dylib \
 *     -Dpathland.gtk.lib=$PWD/../../lib/rust/target/debug/libpathland_gtk.dylib
 * }</pre>
 * On macOS GTK must run on the main thread ({@code -XstartOnFirstThread}); Linux
 * needs no flag. GStreamer must be installed (GTK4 links it) with mp3 decoding
 * plugins for playback.
 */
public final class GtkHost {

    /** Max events drained per wake (× 16 bytes each). */
    private static final int MAX_EVENTS = 64;
    private static final int WINDOW_WIDTH = 1180;
    private static final int WINDOW_HEIGHT = 800;

    public static void main(String[] args) {
        PathlandCore core = PathlandCore.instance();
        try (RingOpcodeSink sink = new RingOpcodeSink(core)) {
            Pointer handle = sink.handle();

            // The session's state + theme, exactly as the server session seeds them.
            PersistentState state = new PersistentState(new InMemoryStateStore(), "desktop");
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

            // The app references its assets with web-style /_pathland/... paths;
            // the renderer resolves them against a local copy extracted once from
            // the shared demo-views jar (see DemoAssets).
            GtkRendererNative renderer = GtkRendererNative.instance();
            renderer.setAssetRoot(DemoAssets.extractToTemp().toString());

            // Wake callback: drain raw EVENT opcodes from the shared ring and route
            // them into the app's bindings. Re-emission (signal → emitter → ring)
            // happens on the GTK main thread, the only writer.
            GtkRendererNative.EventCallback onEvent = () -> {
                Memory buf = new Memory((long) MAX_EVENTS * 16);
                int n = core.drainEvents(handle, buf, MAX_EVENTS);
                if (n > 0) {
                    byte[] raw = buf.getByteArray(0, n * 16);
                    dispatcher.dispatch(reader.decode(raw));
                }
            };

            // Blocks until the window closes; the ring (handle) must outlive it.
            renderer.runRing(core.ringMut(handle), onEvent, WINDOW_WIDTH, WINDOW_HEIGHT);
        }
    }
}