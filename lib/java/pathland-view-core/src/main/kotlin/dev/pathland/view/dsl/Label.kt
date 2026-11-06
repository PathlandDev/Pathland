package dev.pathland.view.dsl

import dev.pathland.view.core.Environment
import dev.pathland.view.core.LabelStyle
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.core.protocol.Components
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals

private object FallbackLabelStyle : LabelStyle {
    override fun makeBody(config: LabelStyle.Configuration): View {
        val parts = buildList {
            config.icon()?.let { add(it) }
            config.title()?.let { add(it) }
        }
        return coreView(Components.HSTACK, parts) { node, _ ->
            node.property(
                dev.pathland.view.core.protocol.Properties.ALIGNMENT,
                Signals.constant(VerticalAlignment.CENTER),
            )
            node.properties[dev.pathland.view.core.protocol.Properties.SPACING] = 2f
        }
    }
}

private class LabelView(
    private val title: Signal<String>?,
    private val icon: Signal<String>?,
    private val iconView: View?,
) : View {

    override fun renderNode(env: Environment): dev.pathland.view.engine.PathlandNode {
        val style = Environment.value(Environment.LABEL_STYLE).get() ?: FallbackLabelStyle
        val titleValue = title?.get()
        val iconValue = icon?.get()
        val titlePart = if (nonBlank(titleValue)) title?.let { textView(it).lineLimit(1) } else null
        val iconPart = iconView ?: if (nonBlank(iconValue)) icon?.let { imageView(it) } else null
        var content: View = style.makeBody(LabelStyle.Configuration(titlePart, iconPart)) ?: groupView(emptyList())
        if (nonBlank(titleValue) && title != null) {
            content = content.accessibilityLabel(title)
        }
        return content.renderNode(env)
    }

    private fun nonBlank(value: String?): Boolean = !value.isNullOrBlank()
}

fun Label(title: String, icon: String? = null): View =
    LabelView(Signals.constant(title), icon?.let { Signals.constant(it) }, null).registerChild()

fun Label(title: Signal<String>, icon: String? = null): View =
    LabelView(title, icon?.let { Signals.constant(it) }, null).registerChild()

fun Label(title: String, icon: View): View {
    ViewScopes.unregister(icon)
    return LabelView(Signals.constant(title), null, icon).registerChild()
}

/** A label whose icon subtree is built in an isolated block (no outer child leak). */
fun Label(title: String, icon: PathlandScope.() -> Unit): View {
    val parts = collectChildren(icon)
    val iconPart = when (parts.size) {
        0 -> null
        1 -> parts[0]
        else -> groupView(parts)
    }
    return LabelView(Signals.constant(title), null, iconPart).registerChild()
}
