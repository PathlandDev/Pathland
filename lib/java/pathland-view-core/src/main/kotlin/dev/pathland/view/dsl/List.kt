package dev.pathland.view.dsl

// @ref spec/DSL.md#list-composite [implements] — the List composite

import dev.pathland.core.protocol.Commands
import dev.pathland.core.protocol.Components
import dev.pathland.core.protocol.Properties
import dev.pathland.view.core.Environment
import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.LabelStyle
import dev.pathland.view.core.ListStyle
import dev.pathland.view.core.Modified
import dev.pathland.view.core.Roles
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.engine.PathlandNode
import dev.pathland.view.core.Color as CoreColor
import kotlin.collections.List as KList

private const val LIST_ROW_PADDING_VERTICAL = 10f
private const val LIST_ROW_PADDING_HORIZONTAL = 16f
private const val LIST_SEPARATOR_WIDTH = 1f
private const val LIST_SEPARATOR_INSET = 16f
private const val LIST_SIDEBAR_PADDING_VERTICAL = 8f
private const val LIST_SIDEBAR_PADDING_HORIZONTAL = 8f
private const val LIST_SIDEBAR_ROW_GAP_OUTER = 2f

private const val LIST_ROW_OVERHEAD = LIST_ROW_PADDING_VERTICAL * 2 + LIST_SEPARATOR_WIDTH
private const val LIST_SIDEBAR_ROW_OVERHEAD =
    LIST_SIDEBAR_PADDING_VERTICAL * 2 + LIST_SIDEBAR_ROW_GAP_OUTER * 2

private fun listBackground(): CoreColor = CoreColor.token("list.background")
private fun listGroupBackground(): CoreColor = CoreColor.token("list.group.background")
private fun listSeparator(): CoreColor = CoreColor.token("list.separator")
private fun listSelectedBackground(): CoreColor = CoreColor.token("list.row.selected.background")
private fun listSidebarBackground(): CoreColor = CoreColor.token("list.sidebar.background")
private fun listSidebarSelectedBackground(): CoreColor = CoreColor.token("list.sidebar.selected.background")
private fun listSidebarSelectedForeground(): CoreColor =
    CoreColor.token("list.sidebar.selected.foreground")
private fun listSidebarForeground(): CoreColor = CoreColor.token("list.sidebar.foreground")
private fun listSidebarIconForeground(): CoreColor = CoreColor.token("list.sidebar.icon.foreground")
private fun listSidebarSelectedIconForeground(): CoreColor =
    CoreColor.token("list.sidebar.selected.icon.foreground")

private fun listDivider(): View = coreView(Components.DIVIDER) { node, _ ->
    node.properties[Properties.COLOR] = listSeparator()
}

private fun listSeparatorRow(inset: Boolean): View {
    val divider = listDivider()
    if (!inset) return divider
    val insetRow = coreView(Components.VSTACK, listOf(divider))
    return insetRow.padding(top = 0f, right = 0f, bottom = 0f, left = LIST_SEPARATOR_INSET)
}

private fun listRowChrome(
    content: View,
    selected: Signal<Boolean>,
    selectedBackground: CoreColor,
    paddingVertical: Float,
    paddingHorizontal: Float,
): View {
    val background = Signals.computed {
        if (selected.get()) selectedBackground else CoreColor.CLEAR
    }
    return content
        .padding(
            top = paddingVertical,
            right = paddingHorizontal,
            bottom = paddingVertical,
            left = paddingHorizontal,
        )
        .frame(width = Commands.Size.FILL)
        .background(background)
}

private fun listRow(
    content: View,
    config: ListStyle.RowConfiguration,
    selectedBackground: CoreColor,
    paddingVertical: Float,
    paddingHorizontal: Float,
    separatorInset: Boolean,
    separators: Boolean,
): View {
    val row = listRowChrome(
        content,
        config.selected(),
        selectedBackground,
        paddingVertical,
        paddingHorizontal,
    )
    if (!separators || config.index() >= config.itemCount() - 1) return row
    return coreView(Components.VSTACK, listOf(row, listSeparatorRow(separatorInset)))
}

/** The default list style: rows with separators and no grouped surface. */
object PlainListStyle : ListStyle {
    override val rowExtentOverhead: Float get() = LIST_ROW_OVERHEAD

    override fun makeRow(content: View, config: ListStyle.RowConfiguration): View =
        listRow(
            content,
            config,
            listSelectedBackground(),
            LIST_ROW_PADDING_VERTICAL,
            LIST_ROW_PADDING_HORIZONTAL,
            separatorInset = false,
            separators = true,
        )
}

/** A grouped list: the full-width surface behind the rows. */
object GroupedListStyle : ListStyle {
    override val rowExtentOverhead: Float get() = LIST_ROW_OVERHEAD

    override fun makeRow(content: View, config: ListStyle.RowConfiguration): View =
        listRow(
            content,
            config,
            listSelectedBackground(),
            LIST_ROW_PADDING_VERTICAL,
            LIST_ROW_PADDING_HORIZONTAL,
            separatorInset = true,
            separators = true,
        )

    override fun makeBody(config: ListStyle.Configuration): View =
        coreView(Components.VSTACK, listOf(config.content()))
            .background(listGroupBackground())
}

/** An inset grouped list: a rounded surface inset from the viewport edges. */
object InsetGroupedListStyle : ListStyle {
    override val rowExtentOverhead: Float get() = LIST_ROW_OVERHEAD

    override fun makeRow(content: View, config: ListStyle.RowConfiguration): View =
        listRow(
            content,
            config,
            listSelectedBackground(),
            LIST_ROW_PADDING_VERTICAL,
            LIST_ROW_PADDING_HORIZONTAL,
            separatorInset = true,
            separators = true,
        )

    override fun makeBody(config: ListStyle.Configuration): View {
        val card = coreView(Components.VSTACK, listOf(config.content()))
            .background(listGroupBackground())
            .border {
                color(listSeparator())
                width(0f)
                radius(Length.token("list.group.radius"))
            }
            .clipped()
        return coreView(Components.VSTACK, listOf(card))
            .padding(Length.token("list.group.inset"))
    }
}

/** An inset list: full-width rows with inset separators and no grouped card. */
object InsetListStyle : ListStyle {
    override val rowExtentOverhead: Float get() = LIST_ROW_OVERHEAD

    override fun makeRow(content: View, config: ListStyle.RowConfiguration): View =
        listRow(
            content,
            config,
            listSelectedBackground(),
            LIST_ROW_PADDING_VERTICAL,
            LIST_ROW_PADDING_HORIZONTAL,
            separatorInset = true,
            separators = true,
        )
}

/** The sidebar row label layout: icon, gap, title, and selected-state colors. */
private class SidebarLabelStyle(private val selected: Signal<Boolean>) : LabelStyle {
    override fun makeBody(config: LabelStyle.Configuration): View {
        val foreground = Signals.computed {
            if (selected.get()) listSidebarSelectedForeground() else listSidebarForeground()
        }
        val iconForeground = Signals.computed {
            if (selected.get()) listSidebarSelectedIconForeground() else listSidebarIconForeground()
        }
        val iconSize = Length.token("space.4")
        val icon = config.icon()
            ?.fontSize(iconSize)
            ?.frame {
                width(iconSize)
                height(iconSize)
            }
            ?.foregroundStyle(iconForeground)
        val title = config.title()
            ?.fontSize(Length.token("control.font.size"))
            ?.fontWeight(FontWeight.SEMIBOLD)
            ?.foregroundStyle(foreground)
        return coreView(Components.HSTACK, listOfNotNull(icon, title)) { node, _ ->
            node.property(
                Properties.ALIGNMENT,
                Signals.constant(VerticalAlignment.CENTER),
            )
            node.properties[Properties.SPACING] = Length.token("space.3")
        }
    }
}

/** A sidebar list: compact rows, no separators, sidebar labels, rounded selection. */
object SidebarListStyle : ListStyle {
    override val rowExtentOverhead: Float get() = LIST_SIDEBAR_ROW_OVERHEAD

    override fun makeRow(content: View, config: ListStyle.RowConfiguration): View {
        val selected = config.selected()
        val row = listRow(
            content.labelStyle(SidebarLabelStyle(selected)),
            config,
            listSidebarSelectedBackground(),
            LIST_SIDEBAR_PADDING_VERTICAL,
            LIST_SIDEBAR_PADDING_HORIZONTAL,
            separatorInset = false,
            separators = false,
        )
        val foreground = Signals.computed {
            if (selected.get()) listSidebarSelectedForeground() else listSidebarForeground()
        }
        val styled = row
            .foregroundStyle(foreground)
            .cornerRadius(Length.token("control.radius"))
        return coreView(Components.VSTACK, listOf(styled))
            .padding(
                top = LIST_SIDEBAR_ROW_GAP_OUTER,
                right = 0f,
                bottom = LIST_SIDEBAR_ROW_GAP_OUTER,
                left = 0f,
            )
    }

    override fun makeBody(config: ListStyle.Configuration): View =
        coreView(Components.VSTACK, listOf(config.content()))
            .background(listSidebarBackground())
            .foregroundStyle(listSidebarForeground())

    override fun viewportBackground(): CoreColor = listSidebarBackground()
}

private fun styleFor(env: Environment): ListStyle =
    Environment.value(Environment.LIST_STYLE).get() ?: PlainListStyle

private fun listRole(view: View): View =
    Modified.props(view, Modified.prop(Properties.ROLE, Roles.LIST.toFloat()))

private fun listItemRole(view: View): View =
    Modified.props(view, Modified.prop(Properties.ROLE, Roles.LIST_ITEM.toFloat()))

private fun finishList(
    env: Environment,
    style: ListStyle,
    rows: View,
    itemCount: Int,
): PathlandNode {
    val styledRows = style.makeBody(ListStyle.Configuration(rows, itemCount))
    val scroll = coreView(Components.SCROLLVIEW, listOf(styledRows))
    val background = style.viewportBackground() ?: listBackground()
    return Modified.props(
        scroll,
        Modified.prop(Properties.BACKGROUND_COLOR, background),
    ).renderNode(env)
}

private class ListRowView(
    private val content: View,
    private val index: Int,
    private val itemCount: Int,
    private val selection: LazySelection?,
) : View {
    override fun renderNode(env: Environment): PathlandNode {
        val style = styleFor(env)
        val selected = Signals.computed { selection?.selectedIndex?.get() == index }
        var row: View = style.makeRow(
            content,
            ListStyle.RowConfiguration(index, itemCount, selected),
        )
        row = row.selected(selected)
        return listItemRole(row).renderNode(env)
    }
}

private class StaticListView(
    private val children: KList<View>,
    private val selection: LazySelection?,
) : View {
    override fun renderNode(env: Environment): PathlandNode {
        val style = styleFor(env)
        val count = children.size
        val geometry = LazySelectionGeometry(LazySelectionKind.STACK, true, 1)
        val rows = children.mapIndexed { index, child ->
            var row: View = ListRowView(child, index, count, selection)
            if (selection != null) {
                row = selection.decorate(index, row, geometry, count)
            }
            row
        }
        val stack = coreView(Components.VSTACK, rows)
        return finishList(env, style, listRole(stack), count)
    }
}

private class LazyListView<T>(
    private val items: Signal<KList<T>>,
    private val itemExtent: Float?,
    private val itemExtents: KList<Float>?,
    private val keyOf: ((T) -> Any)?,
    private val selection: LazySelection?,
    private val content: PathlandScope.(T, Int) -> Unit,
) : View {
    override fun renderNode(env: Environment): PathlandNode {
        val style = styleFor(env)
        val countSignal = Signals.computed { items.get().size }
        val count = countSignal.get()
        val extent = itemExtent?.plus(style.rowExtentOverhead)
        val extents = itemExtents?.map { it + style.rowExtentOverhead }
        if (count > 0) {
            require(extent != null || extents != null) {
                "List requires itemExtent/itemExtents so a virtualized list keeps " +
                    "stable row geometry (spec/DSL.md §Virtualized lists)"
            }
        }
        val data = ForEachView(
            itemsSignal = items,
            keyOf = keyOf,
            itemExtent = extent,
            itemExtents = extents,
            content = { item, index ->
                val base = rowView { content(item, index) }
                +ListRowView(base, index, count, selection)
            },
        )
        val lazy = LazyStackView(
            component = Components.LAZY_VSTACK,
            vertical = true,
            data = data,
            alignment = null,
            spacingSignal = null,
            spacingValue = 0f,
            pin = null,
            visibleRangeListener = null,
            selection = selection,
        )
        return finishList(env, style, listRole(lazy), count)
    }
}

/**
 * A SwiftUI-shaped list composite: `ScrollView + LazyVStack` of rows, styled by
 * the nearest `ListStyle` (spec/DSL.md §List). `itemExtent` is the row
 * **content** extent; the active style's chrome is added by the list.
 *
 * A lazy list requires [itemExtent] or [itemExtents]; a list without an extent
 * uses [List] with static content (eager rows).
 */
fun <T> List(
    items: KList<T>,
    itemExtent: Float? = null,
    itemExtents: KList<Float>? = null,
    key: ((T) -> Any)? = null,
    selection: LazySelection? = null,
    content: PathlandScope.(T, Int) -> Unit,
): View = LazyListView(
    Signals.constant(items),
    itemExtent,
    itemExtents,
    key,
    selection,
    content,
).registerChild()

/** A `List` over a reactive item signal; see the eager [List] overload. */
fun <T> List(
    items: Signal<KList<T>>,
    itemExtent: Float? = null,
    itemExtents: KList<Float>? = null,
    key: ((T) -> Any)? = null,
    selection: LazySelection? = null,
    content: PathlandScope.(T, Int) -> Unit,
): View = LazyListView(
    items,
    itemExtent,
    itemExtents,
    key,
    selection,
    content,
).registerChild()

/** An eager, static-content list. */
@JvmOverloads
fun List(
    selection: LazySelection? = null,
    content: PathlandScope.() -> Unit = {},
): View = StaticListView(collectChildren(content), selection).registerChild()
