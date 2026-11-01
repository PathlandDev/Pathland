package dev.pathland.view.dsl

import dev.pathland.view.Button as JButton
import dev.pathland.view.core.View
import dev.pathland.view.core.signal.Signal

/** A button with a plain-text title. */
fun Button(title: String, action: () -> Unit): View =
    JButton.make { c -> c.title(title).action(action) }.registerChild()

/** A button whose title is bound to a signal. */
fun Button(title: Signal<String>, action: () -> Unit): View =
    JButton.make { c -> c.title(title).action(action) }.registerChild()

/**
 * A button with a custom label: `Button(action = { save() }) { Text("Save") }`.
 * The action is a named argument because the trailing lambda is the label.
 */
fun Button(action: () -> Unit, content: () -> Unit): View {
    val children = collectChildren(content)
    return JButton.make { c -> c.action(action) }
        .children(*children.toTypedArray())
        .registerChild()
}

/** A button with both a title and an explicit label subtree (the subtree wins). */
fun Button(title: String, action: () -> Unit, content: () -> Unit): View {
    val children = collectChildren(content)
    return JButton.make { c -> c.title(title).action(action) }
        .children(*children.toTypedArray())
        .registerChild()
}
