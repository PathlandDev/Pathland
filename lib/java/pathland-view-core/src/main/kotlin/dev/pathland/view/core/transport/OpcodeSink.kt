package dev.pathland.view.core.transport

/**
 * The emission target for opcodes: a self-contained frame sink (SSR/WebSocket) or
 * the zero-copy Rust ring sink.
 */
interface OpcodeSink {
    fun beginFrame()
    fun endFrame()
    fun createNode(id: Int, component: Int)
    fun deleteNode(id: Int)
    fun insertChild(parent: Int, child: Int, index: Int)
    fun removeChild(parent: Int, child: Int)
    fun moveChild(parent: Int, child: Int, newIndex: Int)
    fun setText(nodeId: Int, text: String)
    fun setProperty(nodeId: Int, property: Int, valueType: Int, value: Any)
    fun setDate(nodeId: Int, days: Int, millisOfDay: Int)
    fun setDesignToken(path: String, valueType: Int, value: Any)

    /** Clear every application design-token override. */
    fun resetTheme() {
        throw UnsupportedOperationException("resetTheme")
    }

    /** Emit `META::SET_COLOR_SCHEME` (0 platform, 1 light, 2 dark). */
    fun setColorScheme(mode: Int) {
        throw UnsupportedOperationException("setColorScheme")
    }

    /** Emit `META::SET_WINDOW_METADATA`; an empty string clears the field. */
    fun setWindowMetadata(field: Int, value: String) {
        throw UnsupportedOperationException("setWindowMetadata")
    }

    /** Whether this sink feeds a renderer that gates raw inputs on a `BINDING_ID`. */
    fun requiresBindingId(): Boolean = false
}
