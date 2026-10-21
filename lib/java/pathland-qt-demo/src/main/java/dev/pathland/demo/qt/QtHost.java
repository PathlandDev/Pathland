package dev.pathland.demo.qt;

import dev.pathland.demo.DemoTheme;
import dev.pathland.demo.assets.DemoAssets;
import dev.pathland.demo.music.MusicPlayerView;
import dev.pathland.view.Environment;
import dev.pathland.view.emit.Emitter;
import dev.pathland.view.emit.InputDispatcher;
import dev.pathland.view.emit.RenderResult;
import dev.pathland.view.ffm.PathlandCore;
import dev.pathland.view.ffm.RingOpcodeSink;
import dev.pathland.view.signal.Signals;
import dev.pathland.view.signal.WritableSignal;
import dev.pathland.view.state.InMemoryStateStore;
import dev.pathland.view.state.PersistentState;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;

/**
 * Runs the shared {@link MusicPlayerView} under the native Qt Quick renderer —
 * the cross-language, shared-renderer story for Java.
 *
 * <p>The Java DSL mounts the view through its fine-grained {@link Emitter} into a
 * {@code libpathland_core} shared ring ({@link RingOpcodeSink}, zero-copy). The
 * in-process Qt renderer pumps the same ring in place
 * ({@code pathland_qt_run_ring}); native inputs round-trip as {@code EVENT}
 * opcodes that this host drains and dispatches through {@link InputDispatcher}
 * into the app's bindings (taps, text/value/date inputs, media events). App-driven
 * media (spec/EVENTS.md Media) plays through the renderer's QtMultimedia stream;
 * the demo's tracks + covers are extracted from the classpath and served as the
 * asset root, so the app's web-style {@code /_pathland/assets/...} paths resolve
 * locally.
 *
 * <p>No Swing, no Qt API — only Pathland authoring.
 *
 * <p>Run (one-time: build the Rust dylibs, then the Java reactor):
 * <pre>{@code
 *   cargo build -p pathland-core-capi -p pathland-render-qt
 *   cd lib/java && mvn -q install
 *   cd pathland-qt-demo
 *   MAVEN_OPTS="-XstartOnFirstThread" mvn -q compile exec:java \
 *     -Dpathland.core.lib=$PWD/../../lib/rust/target/debug/libpathland_core.dylib \
 *     -Dpathland.qt.lib=$PWD/../../lib/rust/target/debug/libpathland_qt.dylib
 * }</pre>
 * On macOS Qt must run on the main thread ({@code -XstartOnFirstThread}); Linux
 * needs no flag.
 */
public final class QtHost {

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
                    MusicPlayerView.make(),
                    new Environment(state));
            InputDispatcher dispatcher = new InputDispatcher(result, activePath);
            RingEventReader reader =
                    new RingEventReader(core.ringPtr(handle), core.ringLen(handle));

            // Wake callback: drain raw EVENT opcodes from the shared ring and route
            // them into the app's bindings. Re-emission (signal → emitter → ring)
            // happens on the Qt main thread, the only writer.
            QtRendererNative.EventCallback onEvent = () -> {
                Memory buf = new Memory((long) MAX_EVENTS * 16);
                int n = core.drainEvents(handle, buf, MAX_EVENTS);
                if (n > 0) {
                    byte[] raw = buf.getByteArray(0, n * 16);
                    dispatcher.dispatch(reader.decode(raw));
                }
            };

            // The app references its assets with web-style /_pathland/... paths;
            // the renderer resolves them against a local copy extracted once from
            // the shared demo-views jar (see DemoAssets).
            QtRendererNative renderer = QtRendererNative.instance();
            renderer.setAssetRoot(DemoAssets.extractToTemp().toString());

            // Blocks until the window closes; the ring (handle) must outlive it.
            renderer.runRing(core.ringMut(handle), onEvent, WINDOW_WIDTH, WINDOW_HEIGHT);
        }
    }
}