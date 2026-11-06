package dev.pathland.view.engine

import dev.pathland.view.core.AttributedString
import dev.pathland.view.core.MediaController
import dev.pathland.view.core.ProxyConfig
import dev.pathland.view.core.router.NavOp
import dev.pathland.view.core.router.Router
import dev.pathland.view.core.signal.ConstantSignal
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.transport.Event
import java.util.LinkedHashMap
import java.util.function.Consumer

/** A node in the retained view tree. */
class PathlandNode(
    @JvmField var component: Int,
) {
    @JvmField var id: Int = 0
    @JvmField internal var slot: ViewSlot? = null
    @JvmField internal var parent: PathlandNode? = null
    @JvmField internal var index: Int = -1

    @JvmField var text: String? = null
    /** Parse the text (or text signal) as inline Markdown. */
    @JvmField var markdownText: Boolean = false
    @JvmField var attributedText: AttributedString? = null
    @JvmField val properties: MutableMap<Int, Any?> = LinkedHashMap()
    @JvmField val propertyBindings: MutableMap<Int, Signal<*>> = LinkedHashMap()
    @JvmField var textBinding: Signal<String>? = null
    @JvmField var attributedBinding: Signal<AttributedString>? = null
    @JvmField var dateBinding: Signal<Int>? = null
    @JvmField var days: Int = 0
    @JvmField var millisOfDay: Int = 0
    @JvmField val children: MutableList<PathlandNode> = ArrayList()
    @JvmField val tapActions: MutableList<Runnable> = ArrayList()
    @JvmField var navigateTo: String? = null
    @JvmField var navOp: NavOp = NavOp.NAVIGATE
    @JvmField var textInput: Consumer<String>? = null
    @JvmField var valueInput: Consumer<Float>? = null
    @JvmField var fitSelection: Int = 0
    @JvmField var editingInput: Consumer<Boolean>? = null
    @JvmField var dateInput: DateInput? = null
    @JvmField var mediaInput: MediaInput? = null
    @JvmField var mediaControllerRef: MediaController? = null
    @JvmField var fitInput: Consumer<Int>? = null
    @JvmField var fitReported: Boolean = false
    @JvmField var proxyConfig: ProxyConfig? = null
    @JvmField var navigateHandler: Consumer<Event>? = null
    @JvmField var router: Router? = null
    @JvmField val pathChangeListeners: MutableList<Consumer<String>> = ArrayList()
    @JvmField var activePath: Signal<String>? = null

    /** Whether this node carries reactive (signal-backed) text. */
    fun hasReactiveText(): Boolean = textBinding != null

    /** Whether this node carries reactive (signal-backed) attributed text. */
    fun hasReactiveAttributed(): Boolean = attributedBinding != null

    /** Set a property from a config value that carries a signal. */
    fun property(id: Int, signal: Signal<*>?) {
        if (signal == null) return
        properties[id] = ReactiveValues.initial(id, signal)
        if (signal !is ConstantSignal<*>) {
            propertyBindings[id] = signal
        }
    }

    /** Current text value: the bound signal's value when reactive, else the static text. */
    fun textValue(): String? = textBinding?.get() ?: text

    /** Current attributed value: the bound signal's value when reactive, else the static value. */
    fun attributedValue(): AttributedString? = attributedBinding?.get() ?: attributedText

    companion object {
        @JvmField
        val SNAPSHOT_EMISSION: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
    }
}
