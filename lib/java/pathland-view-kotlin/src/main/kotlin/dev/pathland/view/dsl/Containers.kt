package dev.pathland.view.dsl

import dev.pathland.view.core.Alignment
import dev.pathland.view.Fit as JFit
import dev.pathland.view.Grid as JGrid
import dev.pathland.view.GridItem as JGridItem
import dev.pathland.view.GridRow as JGridRow
import dev.pathland.view.Group as JGroup
import dev.pathland.view.HStack as JHStack
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.LazyHGrid as JLazyHGrid
import dev.pathland.view.LazyHStack as JLazyHStack
import dev.pathland.view.LazyVGrid as JLazyVGrid
import dev.pathland.view.LazyVStack as JLazyVStack
import dev.pathland.view.core.Length as JLength
import dev.pathland.view.ProxyView as JProxyView
import dev.pathland.view.core.ScrollAxis
import dev.pathland.view.ScrollView as JScrollView
import dev.pathland.view.SizeThatFits as JSizeThatFits
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.VStack as JVStack
import dev.pathland.view.core.View
import dev.pathland.view.ZStack as JZStack
import dev.pathland.view.core.signal.Signal

private fun childrenOf(content: () -> Unit): Array<View> = collectChildren(content).toTypedArray()

/* ------------------------------------------------------------------------- */
/* VStack                                                                     */
/* ------------------------------------------------------------------------- */

fun VStack(
    alignment: HorizontalAlignment? = null,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = JVStack.make { c ->
    if (alignment != null) c.alignment(alignment)
    if (spacing != null) c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

fun VStack(
    spacing: Int,
    alignment: HorizontalAlignment? = null,
    content: () -> Unit = {},
): View = VStack(alignment = alignment, spacing = spacing.toFloat(), content = content)

fun VStack(
    spacing: Signal<Float>,
    alignment: HorizontalAlignment? = null,
    content: () -> Unit = {},
): View = JVStack.make { c ->
    if (alignment != null) c.alignment(alignment)
    c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

fun VStack(
    spacing: JLength,
    alignment: HorizontalAlignment? = null,
    content: () -> Unit = {},
): View = JVStack.make { c ->
    if (alignment != null) c.alignment(alignment)
    c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

fun VStack(
    alignment: Signal<HorizontalAlignment>,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = JVStack.make { c ->
    c.alignment(alignment)
    if (spacing != null) c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

/* ------------------------------------------------------------------------- */
/* HStack                                                                     */
/* ------------------------------------------------------------------------- */

fun HStack(
    alignment: VerticalAlignment? = null,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = JHStack.make { c ->
    if (alignment != null) c.alignment(alignment)
    if (spacing != null) c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

fun HStack(
    spacing: Int,
    alignment: VerticalAlignment? = null,
    content: () -> Unit = {},
): View = HStack(alignment = alignment, spacing = spacing.toFloat(), content = content)

fun HStack(
    spacing: Signal<Float>,
    alignment: VerticalAlignment? = null,
    content: () -> Unit = {},
): View = JHStack.make { c ->
    if (alignment != null) c.alignment(alignment)
    c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

fun HStack(
    spacing: JLength,
    alignment: VerticalAlignment? = null,
    content: () -> Unit = {},
): View = JHStack.make { c ->
    if (alignment != null) c.alignment(alignment)
    c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

fun HStack(
    alignment: Signal<VerticalAlignment>,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = JHStack.make { c ->
    c.alignment(alignment)
    if (spacing != null) c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

/* ------------------------------------------------------------------------- */
/* ZStack                                                                     */
/* ------------------------------------------------------------------------- */

fun ZStack(
    alignment: Alignment? = null,
    content: () -> Unit = {},
): View = JZStack.make { c ->
    if (alignment != null) c.alignment(alignment)
}.children(*childrenOf(content)).registerChild()

fun ZStack(
    alignment: Signal<Alignment>,
    content: () -> Unit = {},
): View = JZStack.make { c -> c.alignment(alignment) }
    .children(*childrenOf(content)).registerChild()

/* ------------------------------------------------------------------------- */
/* Grid / GridRow                                                            */
/* ------------------------------------------------------------------------- */

fun Grid(
    columns: Int? = null,
    rows: Int? = null,
    tracks: List<JGridItem>? = null,
    alignment: Alignment? = null,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = JGrid.make { c ->
    if (columns != null) c.columns(columns)
    if (rows != null) c.rows(rows)
    if (tracks != null) c.tracks(tracks)
    if (alignment != null) c.alignment(alignment)
    if (spacing != null) c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

fun GridRow(content: () -> Unit = {}): View =
    JGridRow.make().children(*childrenOf(content)).registerChild()

/* ------------------------------------------------------------------------- */
/* ScrollView                                                                 */
/* ------------------------------------------------------------------------- */

fun ScrollView(
    axis: ScrollAxis = ScrollAxis.VERTICAL,
    content: () -> Unit = {},
): View = JScrollView.make { c -> c.axis(axis) }
    .children(*childrenOf(content)).registerChild()

/* ------------------------------------------------------------------------- */
/* Lazy stacks / grids                                                        */
/* ------------------------------------------------------------------------- */

fun LazyVStack(
    alignment: HorizontalAlignment? = null,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = JLazyVStack.make { c ->
    if (alignment != null) c.alignment(alignment)
    if (spacing != null) c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

fun LazyVStack(
    spacing: Int,
    alignment: HorizontalAlignment? = null,
    content: () -> Unit = {},
): View = LazyVStack(alignment = alignment, spacing = spacing.toFloat(), content = content)

fun LazyVStack(
    spacing: Signal<Float>,
    alignment: HorizontalAlignment? = null,
    content: () -> Unit = {},
): View = JLazyVStack.make { c ->
    if (alignment != null) c.alignment(alignment)
    c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

fun LazyHStack(
    alignment: VerticalAlignment? = null,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = JLazyHStack.make { c ->
    if (alignment != null) c.alignment(alignment)
    if (spacing != null) c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

fun LazyHStack(
    spacing: Int,
    alignment: VerticalAlignment? = null,
    content: () -> Unit = {},
): View = LazyHStack(alignment = alignment, spacing = spacing.toFloat(), content = content)

fun LazyHStack(
    spacing: Signal<Float>,
    alignment: VerticalAlignment? = null,
    content: () -> Unit = {},
): View = JLazyHStack.make { c ->
    if (alignment != null) c.alignment(alignment)
    c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

fun LazyVGrid(
    columns: Int? = null,
    tracks: List<JGridItem>? = null,
    alignment: Alignment? = null,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = JLazyVGrid.make { c ->
    if (columns != null) c.columns(columns)
    if (tracks != null) c.tracks(tracks)
    if (alignment != null) c.alignment(alignment)
    if (spacing != null) c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

fun LazyHGrid(
    rows: Int? = null,
    tracks: List<JGridItem>? = null,
    alignment: Alignment? = null,
    spacing: Float? = null,
    content: () -> Unit = {},
): View = JLazyHGrid.make { c ->
    if (rows != null) c.rows(rows)
    if (tracks != null) c.tracks(tracks)
    if (alignment != null) c.alignment(alignment)
    if (spacing != null) c.spacing(spacing)
}.children(*childrenOf(content)).registerChild()

/* ------------------------------------------------------------------------- */
/* ViewThatFits / Fit                                                         */
/* ------------------------------------------------------------------------- */

/** A candidate in a [ViewThatFits] slot. */
fun Fit(view: View, minWidth: Float = 0f): JFit = JFit.of(view, minWidth)

/** A candidate whose subtree is built in an isolated block (no outer child leak). */
fun Fit(minWidth: Float = 0f, content: () -> Unit): JFit {
    val children = collectChildren(content)
    val view = when (children.size) {
        0 -> JGroup.children()
        1 -> children[0]
        else -> JGroup.children(*children.toTypedArray())
    }
    return JFit.of(view, minWidth)
}

/**
 * The first candidate that fits the available width (SwiftUI `ViewThatFits`).
 * Each view declared in [content] is one candidate, with the default min width.
 */
fun ViewThatFits(content: () -> Unit): View =
    JSizeThatFits.make(*childrenOf(content)).registerChild()

/** The first candidate that fits, with explicit min-width thresholds. */
fun ViewThatFits(vararg fits: JFit): View =
    JSizeThatFits.make(*fits).registerChild()

/* ------------------------------------------------------------------------- */
/* ProxyView                                                                  */
/* ------------------------------------------------------------------------- */

fun ProxyView(
    endpoint: String,
    route: String = "/",
    credential: String? = null,
): View = JProxyView.make { c ->
    c.endpoint(endpoint).route(route)
    if (credential != null) c.credential(credential)
}.registerChild()
