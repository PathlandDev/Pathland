package dev.pathland.view.dsl

import dev.pathland.view.core.ButtonStyle
import dev.pathland.view.core.EmptyContent
import dev.pathland.view.core.Environment
import dev.pathland.view.core.View
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.core.protocol.Components
import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.engine.PathlandNode

internal fun buttonView(
    title: Signal<String>?,
    action: (() -> Unit)?,
    children: List<View>,
): View = coreView(Components.BUTTON) { node, env ->
    val label = children.firstOrNull() ?: title?.let { textView(it) }
    val style = Environment.value(Environment.BUTTON_STYLE).get() ?: PlainButtonStyle
    val content = style.makeBody(ButtonStyle.Configuration(label))
    if (content != null && content !== EmptyContent.INSTANCE) {
        node.children.add(content.render(env))
    }
    val mask = (node.properties[Properties.EVENT_LISTENERS] as? Int ?: 0) or
        Commands.Listeners.POINTER_DOWN or Commands.Listeners.POINTER_UP
    node.properties[Properties.EVENT_LISTENERS] = mask
    if (action != null) node.tapActions.add(Runnable(action))
}

/** A button with a plain-text title. */
fun Button(title: String, action: () -> Unit): View =
    buttonView(dev.pathland.view.core.signal.Signals.constant(title), action, emptyList()).registerChild()

/** A button whose title is bound to a signal. */
fun Button(title: Signal<String>, action: () -> Unit): View =
    buttonView(title, action, emptyList()).registerChild()

/**
 * A button with a custom label: `Button(action = { save() }) { Text("Save") }`.
 * The action is a named argument because the trailing lambda is the label.
 */
fun Button(action: () -> Unit, content: () -> Unit): View {
    val children = collectChildren(content)
    return buttonView(null, action, children).registerChild()
}

/** A button with both a title and an explicit label subtree (the subtree wins). */
fun Button(title: String, action: () -> Unit, content: () -> Unit): View {
    val children = collectChildren(content)
    return buttonView(dev.pathland.view.core.signal.Signals.constant(title), action, children).registerChild()
}
