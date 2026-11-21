package dev.pathland.view.engine

/**
 * A canonical key event delivered to an app-side key handler (spec/EVENTS.md
 * §Keyboard). [keyCode] uses the renderer-shared logical table; [modifiers] is
 * the Shift/Control/Alt/Meta bitmask.
 */
data class KeyEvent(
    @JvmField val keyCode: Int,
    @JvmField val modifiers: Int,
    @JvmField val repeat: Boolean,
)

/** Receives `KEY_DOWN` for a node that declared the `KEY_DOWN` listener bit. */
fun interface KeyInput {
    fun onKeyDown(event: KeyEvent)
}
