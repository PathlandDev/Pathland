package com.pathland.demo.gtk;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.io.File;

/**
 * JNA binding to the Rust GTK renderer cdylib ({@code libpathland_gtk}).
 *
 * <p>The Java DSL emits opcodes into a {@code libpathland_core} ring
 * ({@code com.pathland.view.ffm.RingOpcodeSink}); {@link #runRing} hands that
 * borrowed ring to the in-process GTK renderer ({@code pathland_gtk_run_ring}),
 * which pumps it in place. Native inputs are reported back through a no-payload
 * callback; the host drains them from the ring itself — the wire is the opcode
 * engine, never a side-channel callback.
 *
 * <p>Library resolution: the {@code pathland.gtk.lib} system property, else
 * {@code libpathland_gtk} searched on {@code java.library.path}.
 */
public final class GtkRendererNative {

    /** Invoked (with no payload) on the GTK main thread after a raw input was written. */
    public interface EventCallback extends Callback {
        void invoke();
    }

    /** The C ABI surface, mapped by JNA onto the Rust cdylib. */
    private interface NativeGtkRenderer extends Library {
        void pathland_gtk_run_ring(Pointer ring, EventCallback onEvent, int width, int height);
    }

    private static final class Holder {
        static final GtkRendererNative INSTANCE = new GtkRendererNative();
    }

    /** The process-wide binding; lazily links {@code libpathland_gtk} on first call. */
    public static GtkRendererNative instance() {
        return Holder.INSTANCE;
    }

    private final NativeGtkRenderer nativeGtk;

    private GtkRendererNative() {
        String libraryPath = resolveLibraryPath();
        if (libraryPath == null) {
            throw new IllegalStateException(
                    "libpathland_gtk not found; set -Dpathland.gtk.lib to its full path "
                            + "(cargo build -p pathland-render-gtk)");
        }
        this.nativeGtk = Native.load(libraryPath, NativeGtkRenderer.class);
    }

    /**
     * Run the GTK renderer over the borrowed ring (from
     * {@code PathlandCore.ringMut}). Blocks until the GTK main loop exits (window
     * closed). On macOS the JVM must run with {@code -XstartOnFirstThread} so GTK
     * owns the main thread.
     *
     * @param ring    the opaque ring pointer (must outlive this call)
     * @param onEvent wake callback (nullable)
     * @param width   default window width
     * @param height  default window height
     */
    public void runRing(Pointer ring, EventCallback onEvent, int width, int height) {
        nativeGtk.pathland_gtk_run_ring(ring, onEvent, width, height);
    }

    /** Resolve the native library's full path (system property, then {@code java.library.path}). */
    static String resolveLibraryPath() {
        String override = System.getProperty("pathland.gtk.lib");
        if (override != null) {
            return override;
        }
        String fileName = "libpathland_gtk" + platformLibraryExtension();
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