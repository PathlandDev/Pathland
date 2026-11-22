package dev.pathland.view.dsl

import dev.pathland.view.core.Keys
import dev.pathland.view.core.View
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.engine.KeyEvent
import kotlin.math.min

/** The navigation shape of a lazy container using [LazySelection]. */
internal enum class LazySelectionKind { STACK, GRID }

/** Geometry a [LazySelection] needs to map item indices onto lazy-window units. */
internal data class LazySelectionGeometry(
    val kind: LazySelectionKind,
    val vertical: Boolean,
    val tracks: Int,
)

/**
 * @ref spec/DSL.md#virtualized-lists-lazyvstack--lazyhstack [implements] — LazySelection
 *
 * App-owned selection and keyboard-focus policy for a lazy container.
 *
 * The app supplies the selected item index and the native focus flag (hoisted so
 * it survives `ViewThatFits` swaps). The component supplies the roving tab stop,
 * tap-to-select, arrow/Home/End/Enter navigation, focused-item pinning, and the
 * unfocused-list visible-range clamp. It emits the existing focus/key opcodes;
 * no new protocol surface is involved.
 *
 * `selectedIndex` is an item index, including for grids. Grid geometry is
 * supplied by the lazy container, so the same object works for stacks and grids.
 */
class LazySelection(
    val selectedIndex: WritableSignal<Int>,
    val focusState: WritableSignal<Boolean>,
    val onActivate: (Int) -> Unit = {},
    /** Called after a tap selects and focuses [index]; use it for click-to-activate. */
    val onTap: ((Int) -> Unit)? = null,
    /** Handles keys not consumed by navigation or Enter; return true to consume. */
    val onKeyDown: ((Int, KeyEvent) -> Boolean)? = null,
) {

    internal fun decorate(
        index: Int,
        content: View,
        geometry: LazySelectionGeometry,
        itemCount: Int,
    ): View {
        val selected = computed { selectedIndex.get() == index }
        val isSelected = selected.get()
        val requestFocus = computed { focusState.get() && selectedIndex.get() == index }
        val tapAction = onTap
        val keyAction = onKeyDown

        var row: View = content
            .focusable(isSelected)
            .focused(requestFocus)
            .onKeyDown { key -> handleKey(index, key, geometry, itemCount, keyAction) }
            .onTapGesture {
                selectedIndex.set(index)
                focusState.set(true)
                tapAction?.invoke(index)
            }
        if (isSelected) {
            row = row.onFocusChanged { gained ->
                if (gained) {
                    focusState.set(true)
                    selectedIndex.set(index)
                } else if (selectedIndex.get() == index) {
                    focusState.set(false)
                }
            }
        }
        return row
    }

    /** The lazy-window unit that must stay mounted for [index]. */
    internal fun pinUnit(index: Int, itemCount: Int, geometry: LazySelectionGeometry): Int? {
        if (itemCount <= 0) return null
        val selected = index.coerceIn(0, itemCount - 1)
        return if (geometry.kind == LazySelectionKind.GRID) {
            selected / geometry.tracks.coerceAtLeast(1)
        } else {
            selected
        }
    }

    /**
     * While the container is not focused, keep the roving tab stop on a visible
     * item. Grid reports are in window units; convert them to item indices.
     */
    internal fun clampVisible(
        firstUnit: Int,
        visibleUnits: Int,
        itemCount: Int,
        geometry: LazySelectionGeometry,
    ) {
        if (itemCount <= 0 || visibleUnits <= 0 || focusState.get()) return
        val tracks = geometry.tracks.coerceAtLeast(1)
        val firstItem = (firstUnit.coerceAtLeast(0) * tracks).coerceAtMost(itemCount - 1)
        val lastItem = min(itemCount - 1, firstItem + visibleUnits * tracks - 1)
        val selected = selectedIndex.get()
        if (selected !in firstItem..lastItem) {
            selectedIndex.set(firstItem)
        }
    }

    private fun handleKey(
        index: Int,
        key: KeyEvent,
        geometry: LazySelectionGeometry,
        itemCount: Int,
        keyAction: ((Int, KeyEvent) -> Boolean)?,
    ): Boolean {
        val next = nextIndex(index, key.keyCode, itemCount, geometry)
        if (next != null) {
            if (next != index) selectedIndex.set(next)
            focusState.set(true)
            return true
        }
        if (key.keyCode == Keys.ENTER) {
            onActivate(index)
            return true
        }
        return keyAction?.invoke(index, key) ?: false
    }

    private fun nextIndex(
        index: Int,
        keyCode: Int,
        itemCount: Int,
        geometry: LazySelectionGeometry,
    ): Int? {
        if (itemCount <= 0) return null
        val tracks = geometry.tracks.coerceAtLeast(1)
        if (keyCode == Keys.HOME) return 0
        if (keyCode == Keys.END) return itemCount - 1

        return when (geometry.kind) {
            LazySelectionKind.STACK -> when {
                geometry.vertical && keyCode == Keys.ARROW_UP ->
                    (index - 1).coerceAtLeast(0)
                geometry.vertical && keyCode == Keys.ARROW_DOWN ->
                    (index + 1).coerceAtMost(itemCount - 1)
                !geometry.vertical && keyCode == Keys.ARROW_LEFT ->
                    (index - 1).coerceAtLeast(0)
                !geometry.vertical && keyCode == Keys.ARROW_RIGHT ->
                    (index + 1).coerceAtMost(itemCount - 1)
                else -> null
            }
            LazySelectionKind.GRID -> if (geometry.vertical) {
                when (keyCode) {
                    Keys.ARROW_UP -> if (index - tracks >= 0) index - tracks else index
                    Keys.ARROW_DOWN -> if (index + tracks < itemCount) index + tracks else index
                    Keys.ARROW_LEFT -> if (index % tracks != 0) index - 1 else index
                    Keys.ARROW_RIGHT ->
                        if (index % tracks != tracks - 1 && index + 1 < itemCount) index + 1 else index
                    else -> null
                }
            } else {
                when (keyCode) {
                    Keys.ARROW_LEFT -> if (index - tracks >= 0) index - tracks else index
                    Keys.ARROW_RIGHT -> if (index + tracks < itemCount) index + tracks else index
                    Keys.ARROW_UP -> if (index % tracks != 0) index - 1 else index
                    Keys.ARROW_DOWN ->
                        if (index % tracks != tracks - 1 && index + 1 < itemCount) index + 1 else index
                    else -> null
                }
            }
        }
    }
}
