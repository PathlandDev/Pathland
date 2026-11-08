package dev.pathland.view.dsl

import dev.pathland.core.markdown.Markdown
import dev.pathland.core.text.AttributedString
import dev.pathland.view.core.EmptyContent
import dev.pathland.core.protocol.Commands
import dev.pathland.core.protocol.Components
import dev.pathland.core.protocol.Properties
import dev.pathland.core.protocol.WireValue
import dev.pathland.view.core.signal.Signals
import java.util.function.Consumer
import dev.pathland.view.core.DatePickerMode
import dev.pathland.view.core.ToggleStyle
import dev.pathland.view.engine.DateInput
import dev.pathland.view.core.IconName
import dev.pathland.view.core.MediaController
import dev.pathland.view.core.ShapeKind
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.WritableSignal

/* ------------------------------------------------------------------------- */
/* Color / Length / GridItem data helpers                                     */
/* ------------------------------------------------------------------------- */

/** Color data helpers plus the layout-greedy `Color` view constructor. */
object Color {
    val CLEAR: dev.pathland.view.core.Color get() = dev.pathland.view.core.Color.CLEAR
    val BLACK: dev.pathland.view.core.Color get() = dev.pathland.view.core.Color.BLACK
    val WHITE: dev.pathland.view.core.Color get() = dev.pathland.view.core.Color.WHITE
    val RED: dev.pathland.view.core.Color get() = dev.pathland.view.core.Color.RED
    val BLUE: dev.pathland.view.core.Color get() = dev.pathland.view.core.Color.BLUE

    fun rgb(r: Int, g: Int, b: Int): dev.pathland.view.core.Color = dev.pathland.view.core.Color.rgb(r, g, b)
    fun argb(a: Int, r: Int, g: Int, b: Int): dev.pathland.view.core.Color = dev.pathland.view.core.Color.argb(a, r, g, b)
    fun of(argb: Int): dev.pathland.view.core.Color = dev.pathland.view.core.Color.of(argb)
    fun token(path: String): dev.pathland.view.core.Color = dev.pathland.view.core.Color.token(path)

    /** A solid-color fill view. */
    operator fun invoke(argb: Int): dev.pathland.view.core.View =
        coreView(Components.COLOR) { node, _ -> node.properties[Properties.COLOR] = dev.pathland.view.core.Color.of(argb) }.registerChild()

    /** A solid-color fill view from a data value. */
    operator fun invoke(color: dev.pathland.view.core.Color): dev.pathland.view.core.View =
        coreView(Components.COLOR) { node, _ -> node.properties[Properties.COLOR] = color }.registerChild()
}

/** Length (device-point value or design-token reference) helpers. */
object Length {
    fun of(value: Float) = dev.pathland.view.core.Length.of(value)
    fun token(path: String) = dev.pathland.view.core.Length.token(path)
    operator fun invoke(value: Float) = of(value)
}

/** A grid track spec (`flex`, `fixed:<points>`, or `adaptive:<points>`). */
class GridItem private constructor(val token: String) {
    companion object {
        fun flexible() = GridItem("flex")
        fun fixed(points: Float) = GridItem("fixed:${fmt(points)}")
        fun adaptive(minPoints: Float) = GridItem("adaptive:${fmt(minPoints)}")

        private fun fmt(value: Float): String =
            if (value == kotlin.math.floor(value) && !value.isInfinite()) {
                value.toLong().toString()
            } else {
                value.toString()
            }
    }
}

/* ------------------------------------------------------------------------- */
/* Primitive drawing                                                          */
/* ------------------------------------------------------------------------- */

internal fun textView(text: String): dev.pathland.view.core.View = coreView(Components.TEXT) { node, _ ->
    val constant = Signals.constant(text)
    node.text = text
    node.textBinding = constant
}

internal fun textView(
    text: Signal<String>,
    markdown: Boolean = false,
): dev.pathland.view.core.View = coreView(Components.TEXT) { node, _ ->
    node.text = Signals.untracked { text.get() }
    node.textBinding = text
    node.markdownText = markdown
}

internal fun attributedTextView(value: AttributedString): dev.pathland.view.core.View =
    coreView(Components.TEXT) { node, _ ->
        node.text = value.text
        node.attributedText = value
    }

internal fun attributedTextView(value: Signal<AttributedString>): dev.pathland.view.core.View =
    coreView(Components.TEXT) { node, _ ->
        val initial = Signals.untracked { value.get() }
        node.text = initial.text
        node.attributedText = initial
        node.attributedBinding = value
    }

@JvmOverloads
fun Text(text: String, markdown: Boolean = true): dev.pathland.view.core.View {
    if (!markdown) {
        return textView(text).registerChild()
    }
    val value = Markdown.parseOrPlain(text)
    return (if (value.isPlain) textView(text) else attributedTextView(value)).registerChild()
}

@JvmOverloads
fun Text(text: Signal<String>, markdown: Boolean = true): dev.pathland.view.core.View {
    if (!markdown) {
        return textView(text).registerChild()
    }
    return textView(text, markdown = true).registerChild()
}

fun Text(value: AttributedString): dev.pathland.view.core.View =
    attributedTextView(value).registerChild()

@JvmName("AttributedTextSignal")
fun Text(value: Signal<AttributedString>): dev.pathland.view.core.View =
    attributedTextView(value).registerChild()

internal fun imageView(source: String): dev.pathland.view.core.View = coreView(Components.IMAGE) { node, _ ->
    node.properties[Properties.IMAGE_SOURCE] = source
}

internal fun imageView(source: Signal<String>): dev.pathland.view.core.View = coreView(Components.IMAGE) { node, _ ->
    node.property(Properties.IMAGE_SOURCE, source)
}

internal fun imageView(): dev.pathland.view.core.View = coreView(Components.IMAGE)

fun Image(source: String): dev.pathland.view.core.View = imageView(source).registerChild()

fun Image(source: Signal<String>): dev.pathland.view.core.View = imageView(source).registerChild()

fun Image(): dev.pathland.view.core.View = imageView().registerChild()

private fun iconView(name: Signal<String>, label: String? = null): dev.pathland.view.core.View =
    coreView(Components.ICON) { node, _ ->
        node.property(Properties.ICON_NAME, name)
        if (!label.isNullOrBlank()) node.properties[Properties.LABEL] = label
    }

fun Icon(name: IconName, label: String? = null): dev.pathland.view.core.View =
    iconView(Signals.constant(name.wire()), label).registerChild()

fun Icon(name: String, label: String? = null): dev.pathland.view.core.View =
    iconView(Signals.constant(name), label).registerChild()

fun Icon(name: Signal<String>): dev.pathland.view.core.View =
    iconView(name).registerChild()

private fun shapeView(kind: ShapeKind, cornerRadius: Float? = null): dev.pathland.view.core.View =
    coreView(Components.SHAPE) { node, _ ->
        node.properties[Properties.SHAPE_KIND] = kind.wire().toFloat()
        if (cornerRadius != null) node.properties[Properties.BORDER_RADIUS] = cornerRadius
    }

fun Rectangle(): dev.pathland.view.core.View = shapeView(ShapeKind.RECTANGLE).registerChild()

fun Circle(): dev.pathland.view.core.View = shapeView(ShapeKind.CIRCLE).registerChild()

fun Ellipse(): dev.pathland.view.core.View = shapeView(ShapeKind.ELLIPSE).registerChild()

fun Capsule(): dev.pathland.view.core.View = shapeView(ShapeKind.CAPSULE).registerChild()

fun RoundedRectangle(cornerRadius: Float): dev.pathland.view.core.View =
    shapeView(ShapeKind.ROUNDED_RECTANGLE, cornerRadius).registerChild()

fun Shape(kind: ShapeKind): dev.pathland.view.core.View = shapeView(kind).registerChild()

fun Divider(): dev.pathland.view.core.View = coreView(Components.DIVIDER).registerChild()

fun Spacer(): dev.pathland.view.core.View = coreView(Components.SPACER).registerChild()

fun ProgressView(value: Float): dev.pathland.view.core.View =
    coreView(Components.PROGRESS_VIEW) { node, _ ->
        node.property(Properties.PROGRESS, Signals.constant(value))
    }.registerChild()

fun ProgressView(value: Signal<Float>): dev.pathland.view.core.View =
    coreView(Components.PROGRESS_VIEW) { node, _ ->
        node.property(Properties.PROGRESS, value)
    }.registerChild()

/** An indeterminate progress indicator. */
fun ProgressView(): dev.pathland.view.core.View =
    coreView(Components.PROGRESS_VIEW) { node, _ ->
        node.properties[Properties.IS_INDETERMINATE] = 1
    }.registerChild()

fun Gauge(value: Float, min: Float = 0f, max: Float = 1f): dev.pathland.view.core.View =
    coreView(Components.GAUGE) { node, _ ->
        node.property(Properties.VALUE, Signals.constant(value))
        node.property(Properties.MIN_VALUE, Signals.constant(min))
        node.property(Properties.MAX_VALUE, Signals.constant(max))
    }.registerChild()

fun Gauge(value: Signal<Float>, min: Float = 0f, max: Float = 1f): dev.pathland.view.core.View =
    coreView(Components.GAUGE) { node, _ ->
        node.property(Properties.VALUE, value)
        node.property(Properties.MIN_VALUE, Signals.constant(min))
        node.property(Properties.MAX_VALUE, Signals.constant(max))
    }.registerChild()

/** The picker presentation styles (SwiftUI `PickerStyle` parity). */
enum class PickerStyle(private val wire: Int) : WireValue {
    MENU(0),
    SEGMENTED(1),
    WHEEL(2),
    RADIO_GROUP(3);

    override fun wire(): Int = wire
}

/** A secure text field (`TEXT_FIELD` + `IS_SECURE`). */
fun SecureField(placeholder: String, text: WritableSignal<String>): dev.pathland.view.core.View =
    coreView(Components.TEXT_FIELD) { node, _ ->
        node.property(Properties.PROMPT, Signals.constant(placeholder))
        node.textBinding = text
        node.text = Signals.untracked { text.get() }
        node.textInput = Consumer { text.set(it) }
        node.properties[Properties.IS_SECURE] = 1
    }.registerChild()

fun SecureField(text: WritableSignal<String>, placeholder: String? = null): dev.pathland.view.core.View =
    coreView(Components.TEXT_FIELD) { node, _ ->
        if (placeholder != null) node.property(Properties.PROMPT, Signals.constant(placeholder))
        node.textBinding = text
        node.text = Signals.untracked { text.get() }
        node.textInput = Consumer { text.set(it) }
        node.properties[Properties.IS_SECURE] = 1
    }.registerChild()

/* ------------------------------------------------------------------------- */
/* Containers                                                                 */
/* ------------------------------------------------------------------------- */

fun Group(content: PathlandScope.() -> Unit): dev.pathland.view.core.View =
    groupView(collectChildren(content)).registerChild()

fun <T> ForEach(items: List<T>, content: PathlandScope.(T, Int) -> Unit): dev.pathland.view.core.View =
    coreView(Components.VSTACK) { node, env ->
        items.forEachIndexed { index, item ->
            node.children.add(rowView { content(item, index) }.render(env))
        }
    }.registerChild()

fun <T> ForEach(items: Signal<List<T>>, content: PathlandScope.(T, Int) -> Unit): dev.pathland.view.core.View =
    coreView(Components.VSTACK) { node, env ->
        items.get().forEachIndexed { index, item ->
            node.children.add(rowView { content(item, index) }.render(env))
        }
    }.registerChild()

/* ------------------------------------------------------------------------- */
/* Media                                                                      */
/* ------------------------------------------------------------------------- */

private object FallbackVideoStyle : dev.pathland.view.core.VideoStyle {
    override fun makeBody(config: dev.pathland.view.core.VideoStyle.Configuration): dev.pathland.view.core.View =
        EmptyContent.INSTANCE
}

fun Video(controller: MediaController): dev.pathland.view.core.View =
    coreView(Components.VIDEO) { node, env ->
        val style = dev.pathland.view.core.Environment.value(dev.pathland.view.core.Environment.VIDEO_STYLE).get()
            ?: FallbackVideoStyle
        val content = style.makeBody(dev.pathland.view.core.VideoStyle.Configuration(controller))
        if (content != null && content !== EmptyContent.INSTANCE) node.children.add(content.render(env))
        node.mediaControllerRef = controller
    }.registerChild()

/* ------------------------------------------------------------------------- */
/* Text controls                                                              */
/* ------------------------------------------------------------------------- */

fun TextField(placeholder: String, text: WritableSignal<String>): dev.pathland.view.core.View =
    coreView(Components.TEXT_FIELD) { node, _ ->
        node.property(Properties.PROMPT, Signals.constant(placeholder))
        node.textBinding = text
        node.text = Signals.untracked { text.get() }
        node.textInput = Consumer { text.set(it) }
    }.registerChild()

fun TextField(placeholder: Signal<String>, text: WritableSignal<String>): dev.pathland.view.core.View =
    coreView(Components.TEXT_FIELD) { node, _ ->
        node.property(Properties.PROMPT, placeholder)
        node.textBinding = text
        node.text = Signals.untracked { text.get() }
        node.textInput = Consumer { text.set(it) }
    }.registerChild()

fun TextField(text: WritableSignal<String>, placeholder: String? = null): dev.pathland.view.core.View =
    coreView(Components.TEXT_FIELD) { node, _ ->
        if (placeholder != null) node.property(Properties.PROMPT, Signals.constant(placeholder))
        node.textBinding = text
        node.text = Signals.untracked { text.get() }
        node.textInput = Consumer { text.set(it) }
    }.registerChild()

fun TextEditor(text: WritableSignal<String>): dev.pathland.view.core.View =
    coreView(Components.TEXT_EDITOR) { node, _ ->
        node.textBinding = text
        node.text = Signals.untracked { text.get() }
        node.textInput = Consumer { text.set(it) }
    }.registerChild()

/* ------------------------------------------------------------------------- */
/* Value controls                                                             */
/* ------------------------------------------------------------------------- */

fun Toggle(
    label: String? = null,
    isOn: WritableSignal<Boolean>,
    style: ToggleStyle = ToggleStyle.SWITCH,
): dev.pathland.view.core.View =
    coreView(Components.TOGGLE) { node, _ ->
        node.property(Properties.TOGGLE_STYLE, Signals.constant(style))
        node.property(Properties.SELECTED, isOn)
        node.valueInput = Consumer { isOn.set(it > 0f) }
        if (label != null) {
            node.textBinding = Signals.constant(label)
            node.text = label
        }
    }.registerChild()

fun Toggle(
    label: Signal<String>,
    isOn: WritableSignal<Boolean>,
    style: ToggleStyle = ToggleStyle.SWITCH,
): dev.pathland.view.core.View =
    coreView(Components.TOGGLE) { node, _ ->
        node.property(Properties.TOGGLE_STYLE, Signals.constant(style))
        node.property(Properties.SELECTED, isOn)
        node.valueInput = Consumer { isOn.set(it > 0f) }
        node.textBinding = label
        node.text = Signals.untracked { label.get() }
    }.registerChild()

fun Slider(
    value: WritableSignal<Float>,
    range: ClosedFloatingPointRange<Float>? = null,
    onEditingChanged: ((Boolean) -> Unit)? = null,
): dev.pathland.view.core.View =
    coreView(Components.SLIDER) { node, _ ->
        node.property(Properties.VALUE, value)
        node.valueInput = Consumer { value.set(it) }
        if (range != null) {
            node.properties[Properties.MIN_VALUE] = range.start
            node.properties[Properties.MAX_VALUE] = range.endInclusive
        }
        if (onEditingChanged != null) {
            val current = node.properties[Properties.EVENT_LISTENERS] as? Int ?: 0
            node.properties[Properties.EVENT_LISTENERS] = current or Commands.Listeners.EDITING
            node.editingInput = Consumer { onEditingChanged(it) }
        }
    }.registerChild()

fun Stepper(
    value: WritableSignal<Float>,
    range: ClosedFloatingPointRange<Float>? = null,
    step: Float? = null,
): dev.pathland.view.core.View =
    coreView(Components.STEPPER) { node, _ ->
        node.property(Properties.VALUE, value)
        node.valueInput = Consumer { value.set(it) }
        if (range != null) {
            node.properties[Properties.MIN_VALUE] = range.start
            node.properties[Properties.MAX_VALUE] = range.endInclusive
        }
        if (step != null) node.properties[Properties.STEP_VALUE] = step
    }.registerChild()

fun DatePicker(
    selection: WritableSignal<Int>,
    mode: DatePickerMode = DatePickerMode.DATE,
): dev.pathland.view.core.View =
    coreView(Components.DATE_PICKER) { node, _ ->
        node.property(Properties.DATE_PICKER_MODE, Signals.constant(mode))
        node.days = Signals.untracked { selection.get() }
        node.dateBinding = selection
        node.dateInput = DateInput { days, _ -> selection.set(days) }
    }.registerChild()

fun Picker(
    selection: WritableSignal<Int>,
    style: PickerStyle = PickerStyle.MENU,
    content: PathlandScope.() -> Unit,
): dev.pathland.view.core.View {
    val options = collectChildren(content)
    return coreView(Components.PICKER, options) { node, _ ->
        node.property(Properties.PICKER_STYLE, Signals.constant(style))
        node.property(Properties.SELECTION, selection)
        node.valueInput = Consumer { selection.set(Math.round(it)) }
    }.registerChild()
}

fun Menu(selection: WritableSignal<Int>? = null, content: PathlandScope.() -> Unit): dev.pathland.view.core.View {
    val children = collectChildren(content)
    return coreView(Components.MENU, children) { node, _ ->
        if (selection != null) {
            node.valueInput = Consumer { selection.set(Math.round(it)) }
        }
    }.registerChild()
}

fun ColorPicker(selection: WritableSignal<dev.pathland.view.core.Color>): dev.pathland.view.core.View =
    coreView(Components.COLOR_PICKER) { node, _ ->
        node.property(Properties.COLOR_VALUE, selection)
        node.valueInput = Consumer { selection.set(dev.pathland.view.core.Color(it.toRawBits(), null)) }
    }.registerChild()
