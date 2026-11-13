package dev.pathland.view.dsl

import dev.pathland.core.protocol.Commands
import dev.pathland.core.protocol.Components
import dev.pathland.core.protocol.Properties
import dev.pathland.view.core.Environment
import dev.pathland.view.core.Platform
import dev.pathland.view.core.View
import dev.pathland.view.core.Viewport
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.engine.Emitter
import dev.pathland.view.engine.PathlandNode
import dev.pathland.view.engine.VisibleRangeInput
import kotlin.math.ceil

private const val OVERSCAN = 2
private const val DEFAULT_WINDOW = 20

/**
 * A `ForEach` used as a lazy-stack data source. Outside a lazy stack it renders
 * eagerly (a `VSTACK` of rows); inside one the engine materializes only the
 * visible window (spec/DSL.md §Virtualized lists).
 */
internal class ForEachView<T>(
    private val itemsSignal: Signal<List<T>>,
    private val keyOf: ((T) -> Any)?,
    internal val itemExtent: Float?,
    private val content: PathlandScope.(T, Int) -> Unit,
) : View {

    fun count(): Int = itemsSignal.get().size

    fun row(index: Int): View {
        val list = itemsSignal.get()
        return rowView { content(list[index], index) }
    }

    fun key(index: Int): Any {
        val list = itemsSignal.get()
        return keyOf?.invoke(list[index]) ?: index
    }

    override fun renderNode(env: Environment): PathlandNode {
        val node = PathlandNode(Components.VSTACK)
        val list = itemsSignal.get()
        list.forEachIndexed { index, item ->
            node.children.add(rowView { content(item, index) }.render(env))
        }
        return node
    }
}

/**
 * A windowed `LAZY_VSTACK`/`LAZY_HSTACK`. The app supplies the data source; the
 * engine owns the visible window and reports/consumes `VISIBLE_RANGE`
 * (spec/LAYOUT.md §Virtual extent).
 */
internal class LazyStackView(
    private val component: Int,
    private val vertical: Boolean,
    private val data: ForEachView<*>,
    private val alignment: Signal<*>?,
    private val spacingSignal: Signal<Float>?,
    private val spacingValue: Float?,
) : View {

    private var window: WritableSignal<IntRange>? = null

    override fun renderNode(env: Environment): PathlandNode {
        val node = PathlandNode(component)
        when {
            spacingSignal != null -> node.property(Properties.SPACING, spacingSignal)
            spacingValue != null -> node.properties[Properties.SPACING] = spacingValue
        }
        alignment?.let { node.property(Properties.ALIGNMENT, it) }

        val count = data.count()
        val emitter = Emitter.current()
        val inheritedWindow = emitter?.currentSlotOldLazyWindow()
        val windowSignal = window ?: inheritedWindow ?: Signals.signal(IntRange.EMPTY)
        window = windowSignal

        val extent = when {
            count == 0 -> data.itemExtent ?: emitter?.currentSlotOldLazyExtent() ?: 0f
            data.itemExtent != null -> data.itemExtent
            emitter?.currentSlotOldLazyExtent() != null -> emitter.currentSlotOldLazyExtent()!!
            else -> inferExtent(data, env, vertical)
        }

        var first = 0
        var end = -1
        if (count > 0) {
            val initial = if (windowSignal.get().isEmpty()) {
                Signals.untracked {
                    windowSignal.set(initialWindow(count, extent, vertical))
                    true
                }
                windowSignal.get()
            } else {
                windowSignal.get()
            }
            first = initial.first.coerceIn(0, count - 1)
            end = initial.last.coerceIn(first, count - 1)
            if (first != initial.first || end != initial.last) {
                Signals.untracked { windowSignal.set(first..end); true }
            }
        }

        // Property insertion order follows the protocol id order so the
        // cross-language engine traces match the canonical Rust emitter.
        node.properties[Properties.ITEM_COUNT] = count
        node.properties[Properties.ITEM_EXTENT] = extent
        node.properties[Properties.FIRST_ITEM_INDEX] = first
        node.properties[Properties.EVENT_LISTENERS] =
            (node.properties[Properties.EVENT_LISTENERS] as? Int ?: 0) or
                Commands.Listeners.VISIBLE_RANGE
        node.lazyWindow = windowSignal
        node.lazyExtent = extent
        node.visibleRangeInput = VisibleRangeInput { firstVisible, visibleCount ->
            val total = data.count()
            if (total == 0) {
                windowSignal.set(IntRange.EMPTY)
            } else {
                val start = (firstVisible - OVERSCAN).coerceAtLeast(0)
                val last = (firstVisible + visibleCount + OVERSCAN).coerceAtMost(total) - 1
                windowSignal.set(if (last >= start) start..last else IntRange.EMPTY)
            }
        }

        if (count == 0) {
            node.lazyKeys = emptyList()
            node.lazyNodes = LinkedHashMap()
            return node
        }

        val keys = ArrayList<Any>(end - first + 1)
        val nodes = LinkedHashMap<Any, PathlandNode>()
        for (index in first..end) {
            val key = data.key(index)
            keys.add(key)
            val child = data.row(index).render(env)
            node.children.add(child)
            nodes[key] = child
        }
        node.lazyKeys = keys
        node.lazyNodes = nodes
        return node
    }

    private fun inferExtent(data: ForEachView<*>, env: Environment, vertical: Boolean): Float {
        val probe = Signals.untracked { data.row(0).renderNode(env) }
        val property = if (vertical) Properties.HEIGHT else Properties.WIDTH
        val value = probe.properties[property]
        if (value is Float && value.isFinite() && value > 0f) return value
        val axis = if (vertical) "height" else "width"
        throw IllegalStateException(
            "A virtualized Lazy${if (vertical) "V" else "H"}Stack requires each ForEach row " +
                "to declare a fixed $axis frame (e.g. .frame($axis = 44f)) " +
                "or ForEach(itemExtent = …) (spec/DSL.md §Virtualized lists)",
        )
    }

    private fun initialWindow(count: Int, extent: Float, vertical: Boolean): IntRange {
        val viewport: Viewport? = Environment.value(Platform.VIEWPORT).get()
        val size = if (vertical) viewport?.height ?: 0f else viewport?.width ?: 0f
        val visible = if (size > 0f && extent > 0f) {
            ceil(size / extent).toInt()
        } else {
            DEFAULT_WINDOW
        }
        val items = (visible + OVERSCAN).coerceIn(1, count)
        return 0 until items
    }
}

internal fun lazyVStack(
    alignment: Signal<*>?,
    spacingSignal: Signal<Float>?,
    spacingValue: Float?,
    content: PathlandScope.() -> Unit,
): View {
    val children = collectChildren(content)
    val data = children.singleOrNull() as? ForEachView<*>
    return if (data != null) {
        LazyStackView(Components.LAZY_VSTACK, true, data, alignment, spacingSignal, spacingValue)
            .registerChild()
    } else {
        coreView(Components.LAZY_VSTACK, children) { node, _ ->
            alignment?.let { node.property(Properties.ALIGNMENT, it) }
            when {
                spacingSignal != null -> node.property(Properties.SPACING, spacingSignal)
                spacingValue != null -> node.properties[Properties.SPACING] = spacingValue
            }
        }.registerChild()
    }
}

internal fun lazyHStack(
    alignment: Signal<*>?,
    spacingSignal: Signal<Float>?,
    spacingValue: Float?,
    content: PathlandScope.() -> Unit,
): View {
    val children = collectChildren(content)
    val data = children.singleOrNull() as? ForEachView<*>
    return if (data != null) {
        LazyStackView(Components.LAZY_HSTACK, false, data, alignment, spacingSignal, spacingValue)
            .registerChild()
    } else {
        coreView(Components.LAZY_HSTACK, children) { node, _ ->
            alignment?.let { node.property(Properties.ALIGNMENT, it) }
            when {
                spacingSignal != null -> node.property(Properties.SPACING, spacingSignal)
                spacingValue != null -> node.properties[Properties.SPACING] = spacingValue
            }
        }.registerChild()
    }
}
