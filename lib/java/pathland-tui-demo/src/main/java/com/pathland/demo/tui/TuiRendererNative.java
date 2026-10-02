package com.pathland.demo.tui;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.io.File;

/**
 * JNA binding to the Rust Ratatui renderer cdylib ({@code libpathland_render_tui}).
 *
 * <p>The Java DSL emits opcodes into a {@code libpathland_core} ring
 * ({@code com.pathland.view.ffm.RingOpcodeSink}); {@link #runRing} hands that
 * borrowed ring to the in-process TUI renderer ({@code pathland_tui_run_ring}),
 * which pumps it in place and redraws the terminal. Raw inputs are reported
 * back through a no-payload callback; the host drains them from the ring
 * itself — the wire is the opcode engine, never a side-channel callback.
 *
 * <p>Library resolution: the {@code pathland.tui.lib} system property, else
 * {@code libpathland_render_tui} searched on {@code java.library.path}.
 */
public final class TuiRendererNative {

    /** Invoked (with no payload) after a raw input was written to the ring. */
    public interface EventCallback extends Callback {
        void invoke();
    }

    /** The C ABI surface, mapped by JNA onto the Rust cdylib. */
    private interface NativeTuiRenderer extends Library {
        void pathland_tui_run_ring(Pointer ring, EventCallback onEvent);
    }

    private static final class Holder {
        static final TuiRendererNative INSTANCE = new TuiRendererNative();
    }

    /** The process-wide binding; lazily links {@code libpathland_render_tui} on first call. */
    public static TuiRendererNative instance() {
        return Holder.INSTANCE;
    }

    private final NativeTuiRenderer nativeTui;

    private TuiRendererNative() {
        String libraryPath = resolveLibraryPath();
        if (libraryPath == null) {
            throw new IllegalStateException(
                    "libpathland_render_tui not found; set -Dpathland.tui.lib to its full path "
                            + "(cargo build -p pathland-render-tui)");
        }
        this.nativeTui = Native.load(libraryPath, NativeTuiRenderer.class);
    }

    /**
     * Run the TUI renderer over the borrowed ring (from {@code PathlandCore.ringMut}).
     * Blocks until the user quits ({@code q} / {@code Ctrl+C}).
     *
     * @param ring    the opaque ring pointer (must outlive this call)
     * @param onEvent wake callback (nullable)
     */
    public void runRing(Pointer ring, EventCallback onEvent) {
        nativeTui.pathland_tui_run_ring(ring, onEvent);
    }

    /** Resolve the native library's full path (system property, then {@code java.library.path}). */
    static String resolveLibraryPath() {
        String override = System.getProperty("pathland.tui.lib");
        if (override != null) {
            return override;
        }
        String fileName = "libpathland_render_tui" + platformLibraryExtension();
        String libraryPath = System.getProperty("java.library.path");
        if (libraryPath == null) {
            return null;
        }
        for (String dir : libraryPath.split(File.pathSeparator)) {
            File candidate = new File(dir, fileName);
            if (candidate.isFile()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    private static String platformLibraryExtension() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac")) {
            return ".dylib";
        }
        if (os.contains("win")) {
            return ".dll";
        }
        return ".so";
    }
}