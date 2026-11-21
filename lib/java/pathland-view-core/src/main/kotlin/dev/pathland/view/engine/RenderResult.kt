package dev.pathland.view.engine

import dev.pathland.view.core.ProxyConfig
import dev.pathland.core.transport.Event
import java.util.function.Consumer

/**
 * The result of mounting a view tree: the root id plus the live event-routing
 * maps.
 *
 * Maps are keyed by node id and are the only host-facing surface for input
 * events; forward decoded events to `InputDispatcher`.
 */
data class RenderResult(
    val rootId: Int,
    val tapActions: Map<Int, Runnable>,
    val textInputs: Map<Int, Consumer<String>>,
    val valueInputs: Map<Int, Consumer<Float>>,
    val editingInputs: Map<Int, Consumer<Boolean>>,
    val focusInputs: Map<Int, Consumer<Boolean>>,
    val keyInputs: Map<Int, KeyInput>,
    val dateInputs: Map<Int, DateInput>,
    val mediaInputs: Map<Int, MediaInput>,
    val fitInputs: Map<Int, Consumer<Int>>,
    val visibleRanges: Map<Int, VisibleRangeInput>,
    val navigateActions: Map<Int, Runnable>,
    val navigateHandler: Consumer<Event>?,
    val proxyViews: Map<Int, ProxyConfig>,
) {
    fun rootId(): Int = rootId
    fun tapActions(): Map<Int, Runnable> = tapActions
    fun textInputs(): Map<Int, Consumer<String>> = textInputs
    fun valueInputs(): Map<Int, Consumer<Float>> = valueInputs
    fun editingInputs(): Map<Int, Consumer<Boolean>> = editingInputs
    fun focusInputs(): Map<Int, Consumer<Boolean>> = focusInputs
    fun keyInputs(): Map<Int, KeyInput> = keyInputs
    fun dateInputs(): Map<Int, DateInput> = dateInputs
    fun mediaInputs(): Map<Int, MediaInput> = mediaInputs
    fun fitInputs(): Map<Int, Consumer<Int>> = fitInputs
    fun visibleRanges(): Map<Int, VisibleRangeInput> = visibleRanges
    fun navigateActions(): Map<Int, Runnable> = navigateActions
    fun navigateHandler(): Consumer<Event>? = navigateHandler
    fun proxyViews(): Map<Int, ProxyConfig> = proxyViews
}
