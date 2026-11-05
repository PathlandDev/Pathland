package dev.pathland.view.core.ffm

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import dev.pathland.view.core.protocol.Opcode
import java.io.File
import java.util.Locale

/** JNA binding to the Rust `libpathland_core` cdylib (SPSC ring + opcode engine). */
class PathlandCore private constructor() {

    /** Thrown when the native library cannot be linked. */
    class NativeUnavailableException(message: String, cause: Throwable?) :
        IllegalStateException(message, cause)

    private interface NativePathlandCore : Library {
        fun pathland_core_create(): Pointer
        fun pathland_core_destroy(handle: Pointer)
        fun pathland_ring_buffer_push(handle: Pointer, opcode: Pointer): Boolean
        fun pathland_core_arena_alloc(handle: Pointer, bytes: Pointer, len: Int): Int
        fun pathland_core_begin_frame(handle: Pointer)
        fun pathland_core_end_frame(handle: Pointer)
        fun pathland_core_frame_count(handle: Pointer): Int
        fun pathland_core_ring_ptr(handle: Pointer): Pointer
        fun pathland_core_ring_len(handle: Pointer): Long
        fun pathland_core_ring_mut(handle: Pointer): Pointer
        fun pathland_core_drain_events(handle: Pointer, out: Pointer, max: Int): Int
        fun pathland_core_send_event(handle: Pointer, opcode: Pointer): Boolean
    }

    private val nativeCore: NativePathlandCore

    /** Reused 16-byte opcode buffer (zero per-op heap allocations). */
    private val opcodeBuf = Memory(Opcode.SIZE.toLong())

    init {
        try {
            val libraryPath = resolveLibraryPath()
                ?: throw NativeUnavailableException(
                    "libpathland_core not found on java.library.path; " +
                        "set the system property pathland.core.lib to its full path",
                    null,
                )
            nativeCore = Native.load(libraryPath, NativePathlandCore::class.java)
        } catch (e: NativeUnavailableException) {
            throw e
        } catch (t: Throwable) {
            throw NativeUnavailableException("failed to link libpathland_core", t)
        }
    }

    // --- ring host lifetime ---

    /** Create a ring host over a fresh shared ring. */
    fun create(): Pointer = nativeCore.pathland_core_create()

    /** Destroy a ring host. */
    fun destroy(handle: Pointer) {
        nativeCore.pathland_core_destroy(handle)
    }

    // --- guest → host ring ---

    /** Push one 16-byte opcode into the shared ring. Returns false when full. */
    fun push(handle: Pointer, opcode: ByteArray): Boolean {
        opcodeBuf.write(0, opcode, 0, Opcode.SIZE)
        return nativeCore.pathland_ring_buffer_push(handle, opcodeBuf)
    }

    /** Allocate bytes into the bump arena, returning the absolute offset. */
    fun arenaAlloc(handle: Pointer, bytes: ByteArray): Int {
        val buf = Memory(maxOf(1, bytes.size).toLong())
        if (bytes.isNotEmpty()) {
            buf.write(0, bytes, 0, bytes.size)
        }
        return nativeCore.pathland_core_arena_alloc(handle, buf, bytes.size)
    }

    fun beginFrame(handle: Pointer) {
        nativeCore.pathland_core_begin_frame(handle)
    }

    fun endFrame(handle: Pointer) {
        nativeCore.pathland_core_end_frame(handle)
    }

    fun frameCount(handle: Pointer): Int = nativeCore.pathland_core_frame_count(handle)

    /** Pointer to the shared linear memory (header | ring | arena). */
    fun ringPtr(handle: Pointer): Pointer = nativeCore.pathland_core_ring_ptr(handle)

    /** Byte length of the shared linear memory. */
    fun ringLen(handle: Pointer): Long = nativeCore.pathland_core_ring_len(handle)

    /** Borrow the underlying shared ring for a same-process consumer. */
    fun ringMut(handle: Pointer): Pointer = nativeCore.pathland_core_ring_mut(handle)

    // --- events (host → guest) ---

    /** Drain pending EVENT opcodes into [out]; returns the number written. */
    fun drainEvents(handle: Pointer, out: Pointer, max: Int): Int =
        nativeCore.pathland_core_drain_events(handle, out, max)

    /** Push one 16-byte EVENT opcode into the host → guest event ring. */
    fun sendEvent(handle: Pointer, opcode: ByteArray): Boolean {
        opcodeBuf.write(0, opcode, 0, Opcode.SIZE)
        return nativeCore.pathland_core_send_event(handle, opcodeBuf)
    }

    companion object {
        private object Holder {
            val INSTANCE = PathlandCore()
        }

        /** The process-wide binding; lazily links `libpathland_core` on first call. */
        @JvmStatic
        fun instance(): PathlandCore = Holder.INSTANCE

        /** Resolve the native library's full path. */
        fun resolveLibraryPath(): String? {
            val override = System.getProperty("pathland.core.lib")
            if (override != null) return override
            val fileName = "libpathland_core" + platformLibraryExtension()
            val libraryPath = System.getProperty("java.library.path") ?: return null
            for (dir in libraryPath.split(File.pathSeparator)) {
                val candidate = File(dir, fileName)
                if (candidate.isFile) return candidate.absolutePath
            }
            return null
        }

        private fun platformLibraryExtension(): String {
            val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
            if (os.contains("mac")) return ".dylib"
            if (os.contains("win")) return ".dll"
            return ".so"
        }
    }
}
