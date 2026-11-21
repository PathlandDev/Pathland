package dev.pathland.view.core

import dev.pathland.core.protocol.Commands
import dev.pathland.core.protocol.Properties
import dev.pathland.view.engine.PathlandNode
import java.util.function.Consumer

/**
 * Declares the `FOCUS` listener bit so a node becomes a keyboard focus target,
 * optionally observing renderer focus changes.
 *
 * The renderer makes a node that declares `FOCUS` a tab stop and reports
 * `FOCUS_CHANGED` (spec/EVENTS.md §Text). The app may also request focus with
 * the DSL `focused(...)` modifier (spec/OPCODE.md §META).
 */
internal class FocusableView(
    private val content: View,
    private val enabled: Boolean,
    private val listener: Consumer<Boolean>?,
) : View {
    override fun renderNode(env: Environment): PathlandNode {
        val node = content.renderNode(env)
        if (!enabled) return node
        val existing = node.properties[Properties.EVENT_LISTENERS]
        val mask = if (existing is Int) existing else 0
        node.properties[Properties.EVENT_LISTENERS] = mask or Commands.Listeners.FOCUS
        if (listener != null) node.focusInput = listener
        return node
    }
}

/**
 * Emits a declarative focus request: while the bound signal is true, the
 * renderer focuses this node (the `@FocusState` analog; spec/OPCODE.md §META).
 */
internal class FocusRequestView(
    private val content: View,
    private val focused: dev.pathland.view.core.signal.Signal<Boolean>,
) : View {
    override fun renderNode(env: Environment): PathlandNode {
        focused.get()
        val node = content.renderNode(env)
        node.focusBinding = focused
        return node
    }
}

/**
 * Declares a `KEY_DOWN` listener and records the app-side key handler on the
 * built node. The handler receives canonical key codes (spec/EVENTS.md).
 */
internal class KeyDownView(
    private val content: View,
    private val handler: dev.pathland.view.engine.KeyInput,
) : View {
    override fun renderNode(env: Environment): PathlandNode {
        val node = content.renderNode(env)
        val existing = node.properties[Properties.EVENT_LISTENERS]
        val mask = if (existing is Int) existing else 0
        node.properties[Properties.EVENT_LISTENERS] = mask or Commands.Listeners.KEY_DOWN
        node.keyInput = handler
        return node
    }
}
