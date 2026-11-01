package dev.pathland.view.dsl

import dev.pathland.view.Capsule as JCapsule
import dev.pathland.view.Circle as JCircle
import dev.pathland.view.core.Color as JColor
import dev.pathland.view.DatePicker as JDatePicker
import dev.pathland.view.core.DatePickerMode
import dev.pathland.view.Divider as JDivider
import dev.pathland.view.Ellipse as JEllipse
import dev.pathland.view.ForEach as JForEach
import dev.pathland.view.Gauge as JGauge
import dev.pathland.view.Group as JGroup
import dev.pathland.view.Icon as JIcon
import dev.pathland.view.core.IconName
import dev.pathland.view.Image as JImage
import dev.pathland.view.Label as JLabel
import dev.pathland.view.core.MediaController
import dev.pathland.view.ProgressView as JProgressView
import dev.pathland.view.Rectangle as JRectangle
import dev.pathland.view.RoundedRectangle as JRoundedRectangle
import dev.pathland.view.Shape as JShape
import dev.pathland.view.core.ShapeKind
import dev.pathland.view.Spacer as JSpacer
import dev.pathland.view.Text as JText
import dev.pathland.view.Video as JVideo
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.WritableSignal

/* ------------------------------------------------------------------------- */
/* Color / Length / GridItem data helpers                                     */
/* ------------------------------------------------------------------------- */

/** Color data helpers plus the layout-greedy `Color` view constructor. */
object Color {
    val CLEAR: JColor get() = JColor.CLEAR
    val BLACK: JColor get() = JColor.BLACK
    val WHITE: JColor get() = JColor.WHITE
    val RED: JColor get() = JColor.RED
    val BLUE: JColor get() = JColor.BLUE

    fun rgb(r: Int, g: Int, b: Int): JColor = JColor.rgb(r, g, b)
    fun argb(a: Int, r: Int, g: Int, b: Int): JColor = JColor.argb(a, r, g, b)
    fun of(argb: Int): JColor = JColor.of(argb)
    fun token(path: String): JColor = JColor.token(path)

    /** A solid-color fill view. */
    operator fun invoke(argb: Int): dev.pathland.view.core.View = JColor.of(argb).registerChild()

    /** A solid-color fill view from a data value. */
    operator fun invoke(color: JColor): dev.pathland.view.core.View = color.registerChild()
}

/** Length (device-point value or design-token reference) helpers. */
object Length {
    fun of(value: Float) = dev.pathland.view.core.Length.of(value)
    fun token(path: String) = dev.pathland.view.core.Length.token(path)
    operator fun invoke(value: Float) = of(value)
}

/** Grid track helpers (SwiftUI `GridItem` parity). */
object GridItem {
    fun flexible() = dev.pathland.view.GridItem.flexible()
    fun fixed(points: Float) = dev.pathland.view.GridItem.fixed(points)
    fun adaptive(minPoints: Float) = dev.pathland.view.GridItem.adaptive(minPoints)
}

/* ------------------------------------------------------------------------- */
/* Primitive drawing                                                          */
/* ------------------------------------------------------------------------- */

fun Text(text: String): dev.pathland.view.core.View = JText.make(text).registerChild()

fun Text(text: Signal<String>): dev.pathland.view.core.View = JText.make(text).registerChild()

fun Image(source: String): dev.pathland.view.core.View = JImage.make(source).registerChild()

fun Image(source: Signal<String>): dev.pathland.view.core.View = JImage.make(source).registerChild()

fun Image(): dev.pathland.view.core.View = JImage.make().registerChild()

fun Icon(name: IconName, label: String? = null): dev.pathland.view.core.View {
    val view = if (label == null) JIcon.make(name) else JIcon.labeled(name, label)
    return view.registerChild()
}

fun Icon(name: String, label: String? = null): dev.pathland.view.core.View {
    val builder = if (label == null) JIcon.make(name) else JIcon.make(name).let { b ->
        b.modifiers(dev.pathland.view.Modifiers.accessibilityLabel(label))
    }
    return builder.registerChild()
}

fun Icon(name: Signal<String>): dev.pathland.view.core.View = JIcon.make(name).registerChild()

fun Rectangle(): dev.pathland.view.core.View = JRectangle.make().registerChild()

fun Circle(): dev.pathland.view.core.View = JCircle.make().registerChild()

fun Ellipse(): dev.pathland.view.core.View = JEllipse.make().registerChild()

fun Capsule(): dev.pathland.view.core.View = JCapsule.make().registerChild()

fun RoundedRectangle(cornerRadius: Float): dev.pathland.view.core.View =
    JRoundedRectangle.make(cornerRadius).registerChild()

fun Shape(kind: ShapeKind): dev.pathland.view.core.View = JShape.make(kind).registerChild()

fun Divider(): dev.pathland.view.core.View = JDivider.make().registerChild()

fun Spacer(): dev.pathland.view.core.View = JSpacer.make().registerChild()

fun ProgressView(value: Float): dev.pathland.view.core.View = JProgressView.make(value).registerChild()

fun ProgressView(value: Signal<Float>): dev.pathland.view.core.View =
    JProgressView.make(value).registerChild()

/** An indeterminate progress indicator. */
fun ProgressView(): dev.pathland.view.core.View = JProgressView.indeterminate().registerChild()

fun Gauge(value: Float, min: Float = 0f, max: Float = 1f): dev.pathland.view.core.View =
    JGauge.make { c -> c.value(value).minValue(min).maxValue(max) }.registerChild()

fun Gauge(value: Signal<Float>, min: Float = 0f, max: Float = 1f): dev.pathland.view.core.View =
    JGauge.make { c -> c.value(value).minValue(min).maxValue(max) }.registerChild()

fun Label(title: String, icon: String? = null): dev.pathland.view.core.View =
    JLabel.make { c ->
        c.title(title)
        if (icon != null) c.icon(icon)
    }.registerChild()

fun Label(title: Signal<String>, icon: String? = null): dev.pathland.view.core.View =
    JLabel.make { c ->
        c.title(title)
        if (icon != null) c.icon(icon)
    }.registerChild()

fun Label(title: String, icon: dev.pathland.view.core.View): dev.pathland.view.core.View =
    JLabel.make { c -> c.title(title).icon(icon) }.registerChild()

/** A label whose icon subtree is built in an isolated block (no outer child leak). */
fun Label(title: String, icon: () -> Unit): dev.pathland.view.core.View {
    val parts = collectChildren(icon)
    val iconView = when (parts.size) {
        0 -> JLabel.make(title).registerChild()
        1 -> JLabel.make { c -> c.title(title).icon(parts[0]) }.registerChild()
        else -> JLabel.make { c -> c.title(title).icon(JGroup.children(*parts.toTypedArray())) }.registerChild()
    }
    return iconView
}

private class SecureFieldView(
    private val placeholder: String?,
    private val text: WritableSignal<String>,
) : dev.pathland.view.core.View {
    override fun renderNode(env: dev.pathland.view.core.Environment): dev.pathland.view.engine.PathlandNode {
        val node = dev.pathland.view.TextField.make { c ->
            if (placeholder != null) c.placeholder(placeholder)
            c.text(text)
        }.renderNode(env)
        node.properties.put(dev.pathland.view.core.protocol.Properties.IS_SECURE, 1)
        return node
    }
}

/** A secure text field (`TEXT_FIELD` + `IS_SECURE`). */
fun SecureField(placeholder: String, text: WritableSignal<String>): dev.pathland.view.core.View =
    SecureFieldView(placeholder, text).registerChild()

fun SecureField(text: WritableSignal<String>, placeholder: String? = null): dev.pathland.view.core.View =
    SecureFieldView(placeholder, text).registerChild()

/* ------------------------------------------------------------------------- */
/* Containers                                                                 */
/* ------------------------------------------------------------------------- */

fun Group(content: () -> Unit): dev.pathland.view.core.View {
    val children = collectChildren(content)
    return JGroup.children(*children.toTypedArray()).registerChild()
}

fun <T> ForEach(items: List<T>, content: (T, Int) -> Unit): dev.pathland.view.core.View =
    JForEach.of(items) { item, index -> rowView { content(item, index) } }.registerChild()

fun <T> ForEach(items: Signal<List<T>>, content: (T, Int) -> Unit): dev.pathland.view.core.View =
    JForEach.of(items) { item, index -> rowView { content(item, index) } }.registerChild()

/* ------------------------------------------------------------------------- */
/* Media                                                                      */
/* ------------------------------------------------------------------------- */

fun Video(controller: MediaController): dev.pathland.view.core.View =
    JVideo.make(controller).registerChild()

/** A text field bound to a writable signal (the control owns the two-way binding). */
fun TextField(placeholder: String, text: WritableSignal<String>): dev.pathland.view.core.View =
    dev.pathland.view.TextField.make { c -> c.placeholder(placeholder).text(text) }.registerChild()

fun TextField(placeholder: Signal<String>, text: WritableSignal<String>): dev.pathland.view.core.View =
    dev.pathland.view.TextField.make { c -> c.placeholder(placeholder).text(text) }.registerChild()

fun TextField(text: WritableSignal<String>, placeholder: String? = null): dev.pathland.view.core.View =
    dev.pathland.view.TextField.make { c ->
        if (placeholder != null) c.placeholder(placeholder)
        c.text(text)
    }.registerChild()

fun TextEditor(text: WritableSignal<String>): dev.pathland.view.core.View =
    dev.pathland.view.TextEditor.make { c -> c.text(text) }.registerChild()

fun Toggle(
    label: String? = null,
    isOn: WritableSignal<Boolean>,
    style: dev.pathland.view.core.ToggleStyle = dev.pathland.view.core.ToggleStyle.SWITCH,
): dev.pathland.view.core.View =
    dev.pathland.view.Toggle.make { c ->
        c.style(style).isOn(isOn)
        if (label != null) c.label(label)
    }.registerChild()

fun Toggle(
    label: Signal<String>,
    isOn: WritableSignal<Boolean>,
    style: dev.pathland.view.core.ToggleStyle = dev.pathland.view.core.ToggleStyle.SWITCH,
): dev.pathland.view.core.View =
    dev.pathland.view.Toggle.make { c -> c.style(style).isOn(isOn).label(label) }.registerChild()

fun Slider(
    value: WritableSignal<Float>,
    range: ClosedFloatingPointRange<Float>? = null,
    onEditingChanged: ((Boolean) -> Unit)? = null,
): dev.pathland.view.core.View =
    dev.pathland.view.Slider.make { c ->
        c.value(value)
        val changed = onEditingChanged
        if (range != null) c.`in`(range.start, range.endInclusive)
        if (changed != null) c.onEditingChanged { changed(it) }
    }.registerChild()

fun Stepper(
    value: WritableSignal<Float>,
    range: ClosedFloatingPointRange<Float>? = null,
    step: Float? = null,
): dev.pathland.view.core.View =
    dev.pathland.view.Stepper.make { c ->
        c.value(value)
        if (range != null) c.`in`(range.start, range.endInclusive)
        if (step != null) c.step(step)
    }.registerChild()

fun DatePicker(
    selection: WritableSignal<Int>,
    mode: DatePickerMode = DatePickerMode.DATE,
): dev.pathland.view.core.View =
    JDatePicker.make { c -> c.mode(mode).selection(selection) }.registerChild()

fun Picker(
    selection: WritableSignal<Int>,
    style: dev.pathland.view.PickerStyle = dev.pathland.view.PickerStyle.MENU,
    content: () -> Unit,
): dev.pathland.view.core.View {
    val options = collectChildren(content)
    return dev.pathland.view.Picker.make { c -> c.style(style).selection(selection) }
        .children(*options.toTypedArray())
        .registerChild()
}

fun Menu(selection: WritableSignal<Int>? = null, content: () -> Unit): dev.pathland.view.core.View {
    val children = collectChildren(content)
    return dev.pathland.view.Menu.make { c ->
        if (selection != null) c.selection(selection)
    }.children(*children.toTypedArray()).registerChild()
}

fun ColorPicker(selection: WritableSignal<JColor>): dev.pathland.view.core.View =
    dev.pathland.view.ColorPicker.make { c -> c.selection(selection) }.registerChild()
