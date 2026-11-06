package dev.pathland.demo.qt

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File
import java.util.Locale

/**
 * JNA binding to the Rust Qt renderer cdylib (`libpathland_qt`).
 *
 * The Kotlin DSL emits opcodes into a `libpathland_core` ring
 * ([dev.pathland.view.core.ffm.RingOpcodeSink]); [runRing] hands that borrowed
 * ring to the in-process Qt Quick renderer (`pathland_qt_run_ring`), which pumps
 * it in place. Native inputs are reported back through a no-payload callback;
 * the host drains them from the ring itself.
 */
class QtRendererNative private constructor() {

    /** Invoked (with no payload) on the Qt main thread after a raw input was written. */
    fun interface EventCallback : Callback {
        fun invoke()
    }

    /** The C ABI surface, mapped by JNA onto the Rust cdylib. */
    interface NativeQtRenderer : Library {
        fun pathland_qt_set_asset_root(dir: String)
        fun pathland_qt_run_ring(ring: Pointer, onEvent: EventCallback?, width: Int, height: Int)
    }

    private val nativeQt: NativeQtRenderer

    init {
        val libraryPath = resolveLibraryPath()
            ?: throw IllegalStateException(
                "libpathland_qt not found; set -Dpathland.qt.lib to its full path " +
                    "(cargo build -p pathland-render-qt)",
            )
        nativeQt = Native.load(libraryPath, NativeQtRenderer::class.java)
    }

    /**
     * Set the directory that web-style `/_pathland/...` media/image source paths
     * resolve against. Call before [runRing]; an empty value clears it.
     */
    fun setAssetRoot(dir: String) {
        nativeQt.pathland_qt_set_asset_root(dir)
    }

    /**
     * Run the Qt renderer over the borrowed ring. Blocks until the Qt event loop
     * exits. On macOS the JVM must run with `-XstartOnFirstThread`.
     */
    fun runRing(ring: Pointer, onEvent: EventCallback?, width: Int, height: Int) {
        nativeQt.pathland_qt_run_ring(ring, onEvent, width, height)
    }

    companion object {
        private val INSTANCE: QtRendererNative by lazy { QtRendererNative() }

        /** The process-wide binding; lazily links `libpathland_qt` on first call. */
        fun instance(): QtRendererNative = INSTANCE

        /** Resolve the native library's full path (system property, then `java.library.path`). */
        internal fun resolveLibraryPath(): String? {
            val override = System.getProperty("pathland.qt.lib")
            if (override != null) return override
            val fileName = "libpathland_qt" + platformLibraryExtension()
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
