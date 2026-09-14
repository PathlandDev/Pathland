package com.pathland.view.ffm;

import com.pathland.view.emit.Opcode;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * JNA binding to the Rust {@code libpathland_core} cdylib — the SPSC ring buffer +
 * 16-byte opcode engine.
 *
 * <p>This is the desktop/embedded zero-copy path. Opcodes are written into a reused
 * native buffer (no per-op heap allocation) and pushed into the shared ring. The library
 * is loaded <em>lazily</em> on first use: server apps (Spring Boot / Quarkus) that only
 * emit self-contained frames never touch it.
 *
 * <p>JNA keeps the whole Java stack on every LTS from Java 17 (the FFM API is only final
 * from Java 22, and its incubator variant is per-version unstable).
 *
 * <p>C ABI (see {@code lib/rust/crates/pathland-core-capi}):
 * <pre>
 * void*  pathland_core_create(void);
 * void   pathland_core_destroy(void*);
 * bool   pathland_ring_buffer_push(void*, const uint8_t* opcode); // 16 bytes
 * uint32 pathland_core_arena_alloc(void*, const uint8_t* bytes, uint32 len);
 * void   pathland_core_begin_frame(void*) / pathland_core_end_frame(void*);
 * uint32 pathland_core_frame_count(void*);
 * const uint8_t* pathland_core_ring_ptr(void*);
 * uint64 pathland_core_ring_len(void*);
 * uint32 pathland_core_drain_events(void*, uint8_t* out, uint32 max);
 * bool   pathland_core_send_event(void*, const uint8_t* opcode); // 16 bytes
 * </pre>
 */
public final class PathlandCore {

    /** Thrown when the native library cannot be linked (missing {@code libpathland_core}). */
    public static final class NativeUnavailableException extends IllegalStateException {
        public NativeUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The C ABI surface, mapped by JNA onto the Rust cdylib. */
    private interface NativePathlandCore extends Library {
        Pointer pathland_core_create();
        void pathland_core_destroy(Pointer handle);
        boolean pathland_ring_buffer_push(Pointer handle, Pointer opcode);
        int pathland_core_arena_alloc(Pointer handle, Pointer bytes, int len);
        void pathland_core_begin_frame(Pointer handle);
        void pathland_core_end_frame(Pointer handle);
        int pathland_core_frame_count(Pointer handle);
        Pointer pathland_core_ring_ptr(Pointer handle);
        long pathland_core_ring_len(Pointer handle);
        Pointer pathland_core_ring_mut(Pointer handle);
        int pathland_core_drain_events(Pointer handle, Pointer out, int max);
        boolean pathland_core_send_event(Pointer handle, Pointer opcode);
    }

    private static final class Holder {
        static final PathlandCore INSTANCE = new PathlandCore();
    }

    /** The process-wide binding; lazily links {@code libpathland_core} on first call. */
    public static PathlandCore instance() {
        return Holder.INSTANCE;
    }

    private final NativePathlandCore nativeCore;

    /** Reused 16-byte opcode buffer (zero per-op heap allocations). */
    private final Memory opcodeBuf = new Memory(Opcode.SIZE);

    private PathlandCore() {
        try {
            String libraryPath = resolveLibraryPath();
            if (libraryPath == null) {
                throw new NativeUnavailableException(
                        "libpathland_core not found on java.library.path; "
                                + "set the system property pathland.core.lib to its full path", null);
            }
            nativeCore = Native.load(libraryPath, NativePathlandCore.class);
        } catch (NativeUnavailableException e) {
            throw e;
        } catch (Throwable t) {
            throw new NativeUnavailableException("failed to link libpathland_core", t);
        }
    }

    /**
     * Resolve the native library's full path: the {@code pathland.core.lib} system
     * property if set, else {@code libpathland_core} (with the platform extension)
     * searched on {@code java.library.path}.
     */
    static String resolveLibraryPath() {
        String override = System.getProperty("pathland.core.lib");
        if (override != null) {
            return override;
        }
        String fileName = "libpathland_core" + platformLibraryExtension();
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

    // --- ring host lifetime ---

    /** Create a ring host over a fresh shared ring. */
    public Pointer create() {
        return nativeCore.pathland_core_create();
    }

    /** Destroy a ring host. */
    public void destroy(Pointer handle) {
        nativeCore.pathland_core_destroy(handle);
    }

    // --- guest → host ring ---

    /** Push one 16-byte opcode into the shared ring. Returns false when full. */
    public boolean push(Pointer handle, byte[] opcode) {
        opcodeBuf.write(0, opcode, 0, Opcode.SIZE);
        return nativeCore.pathland_ring_buffer_push(handle, opcodeBuf);
    }

    /** Allocate bytes into the bump arena, returning the absolute offset (or {@code u32::MAX}). */
    public int arenaAlloc(Pointer handle, byte[] bytes) {
        Memory buf = new Memory(bytes.length);
        buf.write(0, bytes, 0, bytes.length);
        return nativeCore.pathland_core_arena_alloc(handle, buf, bytes.length);
    }

    public void beginFrame(Pointer handle) {
        nativeCore.pathland_core_begin_frame(handle);
    }

    public void endFrame(Pointer handle) {
        nativeCore.pathland_core_end_frame(handle);
    }

    public int frameCount(Pointer handle) {
        return nativeCore.pathland_core_frame_count(handle);
    }

    /** Pointer to the shared linear memory (header | ring | arena) for zero-copy reads. */
    public Pointer ringPtr(Pointer handle) {
        return nativeCore.pathland_core_ring_ptr(handle);
    }

    /** Byte length of the shared linear memory. */
    public long ringLen(Pointer handle) {
        return nativeCore.pathland_core_ring_len(handle);
    }

    /**
     * Borrow the underlying shared ring for a same-process consumer (e.g. the GTK
     * renderer pumps it in place). The returned pointer is opaque to Java; hand it
     * to {@code pathland_gtk_run_ring}. The handle must outlive the consumer.
     */
    public Pointer ringMut(Pointer handle) {
        return nativeCore.pathland_core_ring_mut(handle);
    }

    // --- events (host → guest) ---

    /** Drain pending EVENT opcodes into {@code out}; returns the number written. */
    public int drainEvents(Pointer handle, Pointer out, int max) {
        return nativeCore.pathland_core_drain_events(handle, out, max);
    }

    /** Push one 16-byte EVENT opcode into the host → guest event ring. */
    public boolean sendEvent(Pointer handle, byte[] opcode) {
        opcodeBuf.write(0, opcode, 0, Opcode.SIZE);
        return nativeCore.pathland_core_send_event(handle, opcodeBuf);
    }
}