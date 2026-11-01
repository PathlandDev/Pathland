package dev.pathland.view.dsl

import dev.pathland.view.core.Alignment
import dev.pathland.view.core.ProxyConfig
import dev.pathland.view.core.protocol.Components
import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.engine.Emitter
import dev.pathland.view.engine.PathlandNode
import dev.pathland.view.core.Environment
import java.util.function.Consumer
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.Length as JLength
import dev.pathland.view.core.ScrollAxis
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.core.signal.Signal

private fun childrenOf(content: () -> Unit): Array<View> = collectChildren(content).toTypedArray()

/* ------------------------------------------------------------------------- */
/* VStack                                                                     */
/* ------------------------------------------------------------------------- */

fun VStack(
    alignment: HorizontalAlignment? = null,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = coreView(Components.VSTACK, childrenOf(content).toList()) { node, _ ->
    if (alignment != null) node.property(Properties.ALIGNMENT, Signals.constant(alignment))
    if (spacing != null) node.properties[Properties.SPACING] = spacing
}.registerChild()

fun VStack(
    spacing: Int,
    alignment: HorizontalAlignment? = null,
    content: () -> Unit = {},
): View = VStack(alignment = alignment, spacing = spacing.toFloat(), content = content)

fun VStack(
    spacing: Signal<Float>,
    alignment: HorizontalAlignment? = null,
    content: () -> Unit = {},
): View = coreView(Components.VSTACK, childrenOf(content).toList()) { node, _ ->
    if (alignment != null) node.property(Properties.ALIGNMENT, Signals.constant(alignment))
    node.property(Properties.SPACING, spacing)
}.registerChild()

fun VStack(
    spacing: JLength,
    alignment: HorizontalAlignment? = null,
    content: () -> Unit = {},
): View = coreView(Components.VSTACK, childrenOf(content).toList()) { node, _ ->
    if (alignment != null) node.property(Properties.ALIGNMENT, Signals.constant(alignment))
    node.properties[Properties.SPACING] = spacing
}.registerChild()

fun VStack(
    alignment: Signal<HorizontalAlignment>,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = coreView(Components.VSTACK, childrenOf(content).toList()) { node, _ ->
    node.property(Properties.ALIGNMENT, alignment)
    if (spacing != null) node.properties[Properties.SPACING] = spacing
}.registerChild()

/* ------------------------------------------------------------------------- */
/* HStack                                                                     */
/* ------------------------------------------------------------------------- */

fun HStack(
    alignment: VerticalAlignment? = null,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = coreView(Components.HSTACK, childrenOf(content).toList()) { node, _ ->
    if (alignment != null) node.property(Properties.ALIGNMENT, Signals.constant(alignment))
    if (spacing != null) node.properties[Properties.SPACING] = spacing
}.registerChild()

fun HStack(
    spacing: Int,
    alignment: VerticalAlignment? = null,
    content: () -> Unit = {},
): View = HStack(alignment = alignment, spacing = spacing.toFloat(), content = content)

fun HStack(
    spacing: Signal<Float>,
    alignment: VerticalAlignment? = null,
    content: () -> Unit = {},
): View = coreView(Components.HSTACK, childrenOf(content).toList()) { node, _ ->
    if (alignment != null) node.property(Properties.ALIGNMENT, Signals.constant(alignment))
    node.property(Properties.SPACING, spacing)
}.registerChild()

fun HStack(
    spacing: JLength,
    alignment: VerticalAlignment? = null,
    content: () -> Unit = {},
): View = coreView(Components.HSTACK, childrenOf(content).toList()) { node, _ ->
    if (alignment != null) node.property(Properties.ALIGNMENT, Signals.constant(alignment))
    node.properties[Properties.SPACING] = spacing
}.registerChild()

fun HStack(
    alignment: Signal<VerticalAlignment>,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = coreView(Components.HSTACK, childrenOf(content).toList()) { node, _ ->
    node.property(Properties.ALIGNMENT, alignment)
    if (spacing != null) node.properties[Properties.SPACING] = spacing
}.registerChild()

/* ------------------------------------------------------------------------- */
/* ZStack                                                                     */
/* ------------------------------------------------------------------------- */

fun ZStack(
    alignment: Alignment? = null,
    content: () -> Unit = {},
): View = coreView(Components.ZSTACK, childrenOf(content).toList()) { node, _ ->
    if (alignment != null) node.property(Properties.ALIGNMENT, Signals.constant(alignment))
}.registerChild()

fun ZStack(
    alignment: Signal<Alignment>,
    content: () -> Unit = {},
): View = coreView(Components.ZSTACK, childrenOf(content).toList()) { node, _ ->
    node.property(Properties.ALIGNMENT, alignment)
}.registerChild()

/* ------------------------------------------------------------------------- */
/* Grid / GridRow                                                            */
/* ------------------------------------------------------------------------- */

fun Grid(
    columns: Int? = null,
    rows: Int? = null,
    tracks: List<GridItem>? = null,
    alignment: Alignment? = null,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = coreView(Components.GRID, childrenOf(content).toList()) { node, _ ->
    if (alignment != null) node.property(Properties.ALIGNMENT, Signals.constant(alignment))
    if (spacing != null) node.property(Properties.SPACING, Signals.constant(spacing))
    if (tracks != null) node.property(Properties.GRID_TRACKS, Signals.constant(gridTracks(tracks)))
    if (columns != null) node.property(Properties.GRID_COLUMNS, Signals.constant(columns))
    if (rows != null) node.property(Properties.GRID_ROWS, Signals.constant(rows))
}.registerChild()

fun GridRow(content: () -> Unit = {}): View =
    coreView(Components.GRID_ROW, childrenOf(content).toList()).registerChild()

/* ------------------------------------------------------------------------- */
/* ScrollView                                                                 */
/* ------------------------------------------------------------------------- */

fun ScrollView(
    axis: ScrollAxis = ScrollAxis.VERTICAL,
    content: () -> Unit = {},
): View = coreView(Components.SCROLLVIEW, childrenOf(content).toList()) { node, _ ->
    if (axis != ScrollAxis.VERTICAL) node.property(Properties.SCROLL_AXIS, Signals.constant(axis))
}.registerChild()

/* ------------------------------------------------------------------------- */
/* Lazy stacks / grids                                                        */
/* ------------------------------------------------------------------------- */

fun LazyVStack(
    alignment: HorizontalAlignment? = null,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = coreView(Components.LAZY_VSTACK, childrenOf(content).toList()) { node, _ ->
    if (alignment != null) node.property(Properties.ALIGNMENT, Signals.constant(alignment))
    if (spacing != null) node.property(Properties.SPACING, Signals.constant(spacing))
}.registerChild()

fun LazyVStack(
    spacing: Int,
    alignment: HorizontalAlignment? = null,
    content: () -> Unit = {},
): View = LazyVStack(alignment = alignment, spacing = spacing.toFloat(), content = content)

fun LazyVStack(
    spacing: Signal<Float>,
    alignment: HorizontalAlignment? = null,
    content: () -> Unit = {},
): View = coreView(Components.LAZY_VSTACK, childrenOf(content).toList()) { node, _ ->
    if (alignment != null) node.property(Properties.ALIGNMENT, Signals.constant(alignment))
    node.property(Properties.SPACING, spacing)
}.registerChild()

fun LazyHStack(
    alignment: VerticalAlignment? = null,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = coreView(Components.LAZY_HSTACK, childrenOf(content).toList()) { node, _ ->
    if (alignment != null) node.property(Properties.ALIGNMENT, Signals.constant(alignment))
    if (spacing != null) node.property(Properties.SPACING, Signals.constant(spacing))
}.registerChild()

fun LazyHStack(
    spacing: Int,
    alignment: VerticalAlignment? = null,
    content: () -> Unit = {},
): View = LazyHStack(alignment = alignment, spacing = spacing.toFloat(), content = content)

fun LazyHStack(
    spacing: Signal<Float>,
    alignment: VerticalAlignment? = null,
    content: () -> Unit = {},
): View = coreView(Components.LAZY_HSTACK, childrenOf(content).toList()) { node, _ ->
    if (alignment != null) node.property(Properties.ALIGNMENT, Signals.constant(alignment))
    node.property(Properties.SPACING, spacing)
}.registerChild()

fun LazyVGrid(
    columns: Int? = null,
    tracks: List<GridItem>? = null,
    alignment: Alignment? = null,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = coreView(Components.LAZY_VGRID, childrenOf(content).toList()) { node, _ ->
    if (alignment != null) node.property(Properties.ALIGNMENT, Signals.constant(alignment))
    if (spacing != null) node.property(Properties.SPACING, Signals.constant(spacing))
    if (tracks != null) node.property(Properties.GRID_TRACKS, Signals.constant(gridTracks(tracks)))
    if (columns != null) node.property(Properties.GRID_COLUMNS, Signals.constant(columns))
}.registerChild()

fun LazyHGrid(
    rows: Int? = null,
    tracks: List<GridItem>? = null,
    alignment: Alignment? = null,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = coreView(Components.LAZY_HGRID, childrenOf(content).toList()) { node, _ ->
    if (alignment != null) node.property(Properties.ALIGNMENT, Signals.constant(alignment))
    if (spacing != null) node.property(Properties.SPACING, Signals.constant(spacing))
    if (tracks != null) node.property(Properties.GRID_TRACKS, Signals.constant(gridTracks(tracks)))
    if (rows != null) node.property(Properties.GRID_ROWS, Signals.constant(rows))
}.registerChild()

private fun gridTracks(tracks: List<GridItem>): String = tracks.joinToString(",") { it.token }

/* ------------------------------------------------------------------------- */
/* ViewThatFits / Fit                                                         */
/* ------------------------------------------------------------------------- */

/** A `ViewThatFits` candidate: a view plus the minimum width at which it is selected. */
data class FitCandidate(val view: View, val minWidth: Float = 0f)

/** A candidate with an explicit minimum width. */
fun Fit(view: View, minWidth: Float = 0f): FitCandidate {
    // A candidate view constructed inside the enclosing builder block has already
    // auto-registered there; it belongs to the fit slot instead.
    ViewScopes.unregister(view)
    return FitCandidate(view, minWidth)
}

/** A candidate whose subtree is built in an isolated block (no outer child leak). */
fun Fit(minWidth: Float = 0f, content: () -> Unit): FitCandidate {
    val children = collectChildren(content)
    val view = when (children.size) {
        0 -> groupView(emptyList())
        1 -> children[0]
        else -> groupView(children)
    }
    return FitCandidate(view, minWidth)
}

private class FitSlot(private val fits: List<FitCandidate>) : View {
    private val fitIndex = Signals.signal(0)
    private val fitReported = Signals.signal(false)

    override fun renderNode(env: Environment): PathlandNode {
        val node = PathlandNode(Components.SIZE_THAT_FITS)
        node.properties[Properties.FIT_QUERY] = FloatArray(fits.size) { fits[it].minWidth }
        val hint = Emitter.current()?.currentSlotOldFitSelection()
        if (hint != null && hint != fitIndex.get()) {
            Signals.untracked { fitIndex.set(hint); null }
        }
        val index = fitIndex.get().coerceIn(0, (fits.size - 1).coerceAtLeast(0))
        node.fitSelection = index
        node.fitReported = fitReported.get()
        node.fitInput = Consumer { incoming ->
            if (incoming != null && incoming in fits.indices) {
                node.fitReported = true
                node.fitSelection = incoming
                fitIndex.set(incoming)
                fitReported.set(true)
            }
        }
        if (PathlandNode.SNAPSHOT_EMISSION.get() && !fitReported.get()) {
            fits.forEach { node.children.add(it.view.render(env)) }
        } else {
            fits.getOrNull(index)?.let { node.children.add(it.view.render(env)) }
        }
        return node
    }
}

private fun sizeThatFits(fits: Array<FitCandidate>): View {
    val sorted = fits.sortedBy { it.minWidth }
    for (i in sorted.indices) {
        for (j in i + 1 until sorted.size) {
            check(sorted[i].view !== sorted[j].view) {
                "a View instance can be used as the candidate of at most one SizeThatFits Fit"
            }
        }
    }
    return FitSlot(sorted).registerChild()
}

/** The first candidate that fits the available width (SwiftUI `ViewThatFits`). */
fun ViewThatFits(content: () -> Unit): View =
    sizeThatFits(childrenOf(content).map { Fit(it) }.toTypedArray())

/** The first candidate that fits, with explicit min-width thresholds. */
fun ViewThatFits(vararg fits: FitCandidate): View = sizeThatFits(arrayOf(*fits))

/* ------------------------------------------------------------------------- */
/* ProxyView                                                                  */
/* ------------------------------------------------------------------------- */

fun ProxyView(
    endpoint: String,
    route: String = "/",
    credential: String? = null,
): View = coreView(Components.PROXY_VIEW) { node, _ ->
    node.proxyConfig = ProxyConfig(endpoint, route, credential)
}.registerChild()
