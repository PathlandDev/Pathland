package dev.pathland.demo.qt;

import dev.pathland.demo.DemoTheme;
import dev.pathland.demo.SplitNavDemo;
import dev.pathland.demo.assets.DemoAssets;
import dev.pathland.view.Environment;
import dev.pathland.view.Platform;
import dev.pathland.view.emit.Emitter;
import dev.pathland.view.emit.InputDispatcher;
import dev.pathland.view.emit.RenderResult;
import dev.pathland.view.ffm.PathlandCore;
import dev.pathland.view.ffm.RingOpcodeSink;
import dev.pathland.view.signal.Signals;
import dev.pathland.view.signal.WritableSignal;
import dev.pathland.view.state.InMemoryStateStore;
import dev.pathland.view.state.PersistentState;

import static dev.pathland.view.Modifiers.environmentBinding;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;

/**
 * Runs the shared demo views ({@link SplitNavDemo}) under the native Qt Quick
 * renderer — the cross-language, shared-renderer story for Java, including
 * native navigation (the Rust DSL has no NavigationContainer yet, so this is
 * the nav demo vehicle).
 *
 * <p>The Java DSL mounts the view through its fine-grained {@link Emitter} into a
 * {@code libpathland_core} shared ring ({@link RingOpcodeSink}, zero-copy). The
 * in-process Qt renderer pumps the same ring in place
 * ({@code pathland_qt_run_ring}); native inputs round-trip as {@code EVENT}
 * opcodes that this host drains and dispatches through {@link InputDispatcher}
 * into the app's bindings (taps, text/value/date inputs, router back).
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
    private static final int WINDOW_WIDTH = 1100;
    private static final int WINDOW_HEIGHT = 720;

    public static void main(String[] args) {
        PathlandCore core = PathlandCore.instance();
        try (RingOpcodeSink sink = new RingOpcodeSink(core)) {
            Pointer handle = sink.handle();

            // The session's state + active path, exactly as the server session seeds
            // them — Platform.ACTIVE_PATH is host-provided; SplitNavDemo reads it and
            // builds a bound router.
            PersistentState state = new PersistentState(new InMemoryStateStore(), "desktop");
            WritableSignal<String> activePath = Signals.signal("/home");

            Emitter emitter = new Emitter(sink, DemoTheme.adaptive());
            // The active platform path is a host-provided environment signal; the
            // view's router binds to it (spec DSL.md §4.5).
            RenderResult result = emitter.mount(
                    new SplitNavDemo()
                            .modifiers(environmentBinding(
                                    e -> e.key(Platform.ACTIVE_PATH).value(activePath))),
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