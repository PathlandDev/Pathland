package com.pathland.render.html;

import com.pathland.view.emit.Frame;
import com.pathland.view.transport.FrameCodec;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.io.IOException;
import java.io.FileOutputStream;

/**
 * The Java binding to the Rust HTML renderer ({@code libpathland_render_html}) —
 * a thin JNA shim over the flat C ABI. **Stateless and streaming**: each call
 * renders a self-contained snapshot frame to HTML in one pass (no retained tree).
 *
 * <p>This replaces the former hand-written retained Java renderer, so there is a
 * single renderer reused by every language (Rust SSR, Java via JNA, Swift/.NET via
 * the same ABI). The wire codec ({@link FrameCodec}) provides the self-contained
 * batch bytes the C ABI consumes.
 *
 * <p>C ABI (see {@code lib/rust/crates/pathland-render-html/src/capi.rs}):
 * <pre>
 * const char* pathland_html_render(const uint8_t* batch, uint32 len, uint32 root);
 * const char* pathland_html_render_fragment(const uint8_t* batch, uint32 len, uint32 root);
 * const char* pathland_html_render_debug(const uint8_t* batch, uint32 len, uint32 root, uint8 debug);
 * const char* pathland_html_render_fragment_debug(const uint8_t* batch, uint32 len, uint32 root, uint8 debug);
 * void        pathland_html_free(const char* ptr);
 * </pre>
 */
public final class HtmlRenderer {

    /** Thrown when the native library cannot be linked (missing {@code libpathland_render_html}). */
    public static final class NativeUnavailableException extends IllegalStateException {
        public NativeUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The C ABI surface, mapped by JNA onto the Rust cdylib. */
    private interface NativeRenderHtml extends Library {
        Pointer pathland_html_render(Pointer batch, int len, int root);
        Pointer pathland_html_render_fragment(Pointer batch, int len, int root);
        Pointer pathland_html_render_debug(Pointer batch, int len, int root, byte debug);
        Pointer pathland_html_render_fragment_debug(Pointer batch, int len, int root, byte debug);
        void pathland_html_free(Pointer ptr);
    }

    private static final class Holder {
        static final HtmlRenderer INSTANCE = new HtmlRenderer();
    }

    /** The process-wide binding; lazily links {@code libpathland_render_html} on first call. */
    public static HtmlRenderer instance() {
        return Holder.INSTANCE;
    }

    /**
     * A renderer if the native library can be linked, or {@code null} when it is
     * unavailable (not embedded, not on java.library.path). Callers that can degrade
     * gracefully (e.g. the Tailwind CSS endpoint) should use this instead of
     * {@link #instance()}, which throws on a missing native library.
     */
    public static HtmlRenderer tryInstance() {
        try {
            return instance();
        } catch (NativeUnavailableException e) {
            return null;
        }
    }

    /** True when the native renderer is linkable (embedded or on java.library.path). */
    public static boolean isAvailable() {
        return tryInstance() != null;
    }

    private final NativeRenderHtml nativeRenderer;

    private HtmlRenderer() {
        String path = resolveLibraryPath();
        if (path == null) {
            throw new NativeUnavailableException(
                    "libpathland_render_html not found (not embedded in the jar, not on java.library.path, "
                            + "pathland.render.html.lib not set); build the Rust crate so the dylib is embedded, "
                            + "or set pathland.render.html.lib", null);
        }
        try {
            nativeRenderer = Native.load(path, NativeRenderHtml.class);
        } catch (Throwable t) {
            throw new NativeUnavailableException("failed to link libpathland_render_html: " + path, t);
        }
    }

    /**
     * Resolve the native library's full path, in priority order:
     * <ol>
     *   <li>the {@code pathland.render.html.lib} system property (explicit override),</li>
     *   <li>the dylib **embedded in the jar** at {@code META-INF/native/} — extracted to a
     *       temp dir via JNA so it always travels with the artifact,</li>
     *   <li>the local Rust build ({@code ../../rust/target/debug}) for the dev flow,</li>
     *   <li>{@code java.library.path}.</li>
     * </ol>
     */
    static String resolveLibraryPath() {
        String override = System.getProperty("pathland.render.html.lib");
        if (override != null) {
            return override;
        }
        // 2. Embedded in the jar (META-INF/native/...).
        String embedded = extractEmbedded();
        if (embedded != null) {
            return embedded;
        }
        // 3. Local Rust debug build (dev flow).
        String devBuild = new java.io.File(
                new java.io.File(System.getProperty("user.dir")).getParentFile(), "rust/target/debug").getAbsolutePath();
        java.io.File devLib = new java.io.File(devBuild, "libpathland_render_html" + platformLibraryExtension());
        if (devLib.isFile()) {
            return devLib.getAbsolutePath();
        }
        // 4. java.library.path.
        String fileName = "libpathland_render_html" + platformLibraryExtension();
        String libraryPath = System.getProperty("java.library.path");
        if (libraryPath == null) {
            return null;
        }
        for (String dir : libraryPath.split(java.io.File.pathSeparator)) {
            java.io.File candidate = new java.io.File(dir, fileName);
            if (candidate.isFile()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    /** Extract the jar-embedded dylib to a temp dir; returns its path or null. */
    private static String extractEmbedded() {
        String fileName = "libpathland_render_html" + platformLibraryExtension();
        try (var stream = HtmlRenderer.class.getResourceAsStream("/META-INF/native/" + fileName)) {
            if (stream == null) {
                return null;
            }
            java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"),
                    "pathland-native-" + HtmlRenderer.class.getSimpleName() + "-" + System.nanoTime());
            if (!dir.mkdirs()) {
                return null;
            }
            java.io.File out = new java.io.File(dir, fileName);
            try (var copy = new java.io.FileOutputStream(out)) {
                stream.transferTo(copy);
            }
            if (!out.setExecutable(true, false)) {
                // non-fatal: some platforms lack POSIX perms
            }
            return out.getAbsolutePath();
        } catch (IOException e) {
            return null;
        }
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

    /**
     * Render a full snapshot frame as a complete HTML document. The frame is the
     * self-contained mount/snapshot batch (TREE + STYLE) the emitter produced.
     */
    public String render(Frame frame, int root) {
        return renderFrame(frame, root, true);
    }

    /** Render a full snapshot frame as an HTML fragment (no {@code <html>}). */
    public String renderFragment(Frame frame, int root) {
        return renderFrame(frame, root, false);
    }

    /** Alias of {@link #renderFragment} for the {@code META::RESYNC} snapshot path. */
    public String renderFullSnapshot(Frame frame, int root) {
        return renderFrame(frame, root, false);
    }

    /**
     * Render a full snapshot frame as a complete HTML document with **debug
     * comments** on every node: each element is prefixed with an HTML comment
     * naming its component type and the modifiers applied (e.g.
     * {@code <!-- #1 VStack: spacing=4, alignment=Fill -->}). Debugging aid —
     * opt-in; the plain {@link #render} output is unchanged.
     */
    public String renderDebug(Frame frame, int root) {
        return renderFrameDebug(frame, root, true);
    }

    /**
     * Render a snapshot frame as an HTML fragment with **debug comments** on
     * every node (see {@link #renderDebug}).
     */
    public String renderFragmentDebug(Frame frame, int root) {
        return renderFrameDebug(frame, root, false);
    }

    private String renderFrame(Frame frame, int root, boolean fullDocument) {
        return renderFrame(frame, root, fullDocument, false);
    }

    private String renderFrameDebug(Frame frame, int root, boolean fullDocument) {
        return renderFrame(frame, root, fullDocument, true);
    }

    private String renderFrame(Frame frame, int root, boolean fullDocument, boolean debug) {
        byte[] bytes = FrameCodec.encodeFrame(frame);
        try (com.sun.jna.Memory memory = new com.sun.jna.Memory(bytes.length)) {
            memory.write(0, bytes, 0, bytes.length);
            Pointer result;
            if (debug) {
                byte flag = 1;
                result = fullDocument
                        ? nativeRenderer.pathland_html_render_debug(memory, bytes.length, root, flag)
                        : nativeRenderer.pathland_html_render_fragment_debug(memory, bytes.length, root, flag);
            } else {
                result = fullDocument
                        ? nativeRenderer.pathland_html_render(memory, bytes.length, root)
                        : nativeRenderer.pathland_html_render_fragment(memory, bytes.length, root);
            }
            return take(result);
        }
    }

    /**
     * Copy a NUL-terminated C string into a Java String and free the native buffer. */
    private static String take(Pointer ptr) {
        if (ptr == null) {
            return "";
        }
        try {
            return ptr.getString(0);
        } finally {
            nativeFree(ptr);
        }
    }

    private static void nativeFree(Pointer ptr) {
        // Resolve through the holder to avoid re-initializing on the free path.
        try {
            Holder.INSTANCE.nativeRenderer.pathland_html_free(ptr);
        } catch (Throwable ignored) {
            // best-effort; the buffer is transient
        }
    }
}