package dev.pathland.view.dsl

import dev.pathland.view.core.Alignment
import dev.pathland.view.core.ButtonStyle
import dev.pathland.view.core.Color
import dev.pathland.view.core.ContentMode
import dev.pathland.view.core.ControlSize
import dev.pathland.view.core.EnvironmentBinding
import dev.pathland.view.core.EnvironmentKey
import dev.pathland.view.core.FontDesign
import dev.pathland.view.core.FontStyle
import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.LabelStyle
import dev.pathland.view.core.Length
import dev.pathland.view.core.Modified
import dev.pathland.view.core.PathChange
import dev.pathland.view.core.PointerEvents
import dev.pathland.view.core.ShapeKind
import dev.pathland.view.core.TapGesture
import dev.pathland.view.core.TextAlignment
import dev.pathland.view.core.TextCase
import dev.pathland.view.core.Truncation
import dev.pathland.view.core.VideoStyle
import dev.pathland.view.core.View
import dev.pathland.view.core.ViewModifier
import dev.pathland.view.core.WindowMetadata
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import java.util.function.Consumer

private fun props(vararg props: Modified.Prop): ViewModifier = object : ViewModifier {
    override fun body(content: View): View = Modified.props(content, *props)
}

private fun prop(property: Int, value: Any): ViewModifier = props(Modified.prop(property, value))

private fun prop(property: Int, signal: Signal<*>): ViewModifier = props(Modified.prop(property, signal))

/** A text/font specification value; also a modifier. */
class Font private constructor(
    private val style: dev.pathland.view.core.TextStyle?,
    private val family: String?,
    private val size: Float?,
    private val weight: FontWeight?,
    private val design: FontDesign?,
) : ViewModifier {

    override fun body(content: View): View {
        if (style != null) return Modified.props(content, Modified.prop(Properties.TEXT_STYLE, style.code().toFloat()))
        val list = mutableListOf<Modified.Prop>()
        if (family != null) list += Modified.prop(Properties.FONT_FAMILY, family)
        if (size != null) list += Modified.prop(Properties.FONT_SIZE, size)
        if (weight != null) list += Modified.prop(Properties.FONT_WEIGHT, weight.wire().toFloat())
        if (design != null) list += Modified.prop(Properties.FONT_DESIGN, design.wire().toFloat())
        return Modified.props(content, *list.toTypedArray())
    }

    companion object {
        fun largeTitle() = Font(dev.pathland.view.core.TextStyle.LARGE_TITLE, null, null, null, null)
        fun title() = Font(dev.pathland.view.core.TextStyle.TITLE, null, null, null, null)
        fun title2() = Font(dev.pathland.view.core.TextStyle.TITLE2, null, null, null, null)
        fun title3() = Font(dev.pathland.view.core.TextStyle.TITLE3, null, null, null, null)
        fun headline() = Font(dev.pathland.view.core.TextStyle.HEADLINE, null, null, null, null)
        fun subheadline() = Font(dev.pathland.view.core.TextStyle.SUBHEADLINE, null, null, null, null)
        fun body() = Font(dev.pathland.view.core.TextStyle.BODY, null, null, null, null)
        fun callout() = Font(dev.pathland.view.core.TextStyle.CALLOUT, null, null, null, null)
        fun footnote() = Font(dev.pathland.view.core.TextStyle.FOOTNOTE, null, null, null, null)
        fun caption() = Font(dev.pathland.view.core.TextStyle.CAPTION, null, null, null, null)
        fun caption2() = Font(dev.pathland.view.core.TextStyle.CAPTION2, null, null, null, null)
        fun custom(family: String, size: Float) = Font(null, family, size, null, null)
        fun custom(family: String, size: Float, weight: FontWeight, design: FontDesign) =
            Font(null, family, size, weight, design)
        fun system(size: Float) = Font(null, null, size, null, null)
        fun system(size: Float, weight: FontWeight) = Font(null, null, size, weight, null)
        fun system(size: Float, weight: FontWeight, design: FontDesign) =
            Font(null, null, size, weight, design)
    }
}

class Padding private constructor(private val config: Config) : ViewModifier {
    class Config {
        var uniform: Signal<Float>? = null
        var uniformToken: Length? = null
        var top: Signal<Float>? = null
        var right: Signal<Float>? = null
        var bottom: Signal<Float>? = null
        var left: Signal<Float>? = null

        fun uniform(value: Float) = apply { uniform = Signals.constant(value); uniformToken = null; clearEdges() }
        fun uniform(value: Int) = uniform(value.toFloat())
        fun uniform(value: Signal<Float>) = apply { uniform = value; uniformToken = null; clearEdges() }
        fun uniform(value: Length) = apply { uniformToken = value; uniform = null; clearEdges() }
        fun edges(top: Float, right: Float = top, bottom: Float = top, left: Float = right) = apply {
            uniform = null; uniformToken = null
            this.top = Signals.constant(top); this.right = Signals.constant(right)
            this.bottom = Signals.constant(bottom); this.left = Signals.constant(left)
        }
        private fun clearEdges() { top = null; right = null; bottom = null; left = null }
    }

    override fun body(content: View): View {
        config.uniformToken?.let { return Modified.props(content, Modified.prop(Properties.PADDING, it)) }
        val uniform = config.uniform
        if (uniform != null || config.top == null) {
            return Modified.props(content, Modified.prop(Properties.PADDING, uniform ?: Signals.constant(0f)))
        }
        return Modified.props(
            content,
            Modified.prop(Properties.PADDING_TOP, config.top!!),
            Modified.prop(Properties.PADDING_RIGHT, config.right!!),
            Modified.prop(Properties.PADDING_BOTTOM, config.bottom!!),
            Modified.prop(Properties.PADDING_LEFT, config.left!!),
        )
    }

    companion object {
        fun of(value: Float) = Padding(Config().uniform(value))
        fun of(value: Signal<Float>) = Padding(Config().uniform(value))
        fun of(value: Length) = Padding(Config().uniform(value))
        fun of(configure: Config.() -> Unit) = Padding(Config().apply(configure))
    }
}

class Frame private constructor(private val config: Config) : ViewModifier {
    class Config {
        var width: Signal<Float>? = null
        var height: Signal<Float>? = null
        var widthToken: Length? = null
        var heightToken: Length? = null
        var alignment: Signal<Alignment>? = null
        val bounds: Array<Signal<Float>?> = arrayOfNulls(6)

        fun width(value: Float) = apply { width = size(value); widthToken = null }
        fun width(value: Signal<Float>) = apply { width = value; widthToken = null }
        fun width(value: Length) = apply { widthToken = value; width = null }
        fun height(value: Float) = apply { height = size(value); heightToken = null }
        fun height(value: Signal<Float>) = apply { height = value; heightToken = null }
        fun height(value: Length) = apply { heightToken = value; height = null }
        fun minWidth(value: Float) = bound(0, value)
        fun minWidth(value: Signal<Float>) = apply { bounds[0] = value }
        fun idealWidth(value: Float) = bound(1, value)
        fun idealWidth(value: Signal<Float>) = apply { bounds[1] = value }
        fun maxWidth(value: Float) = bound(2, value)
        fun maxWidth(value: Signal<Float>) = apply { bounds[2] = value }
        fun minHeight(value: Float) = bound(3, value)
        fun minHeight(value: Signal<Float>) = apply { bounds[3] = value }
        fun idealHeight(value: Float) = bound(4, value)
        fun idealHeight(value: Signal<Float>) = apply { bounds[4] = value }
        fun maxHeight(value: Float) = bound(5, value)
        fun maxHeight(value: Signal<Float>) = apply { bounds[5] = value }
        fun alignment(value: Alignment) = apply { alignment = Signals.constant(value) }
        fun alignment(value: Signal<Alignment>) = apply { alignment = value }
        private fun bound(i: Int, value: Float) = apply {
            if (!value.isNaN() && !value.isInfinite()) bounds[i] = Signals.constant(value)
        }
        private fun size(value: Float): Signal<Float>? =
            if (value.isNaN()) null else Signals.constant(if (value.isInfinite()) Commands.Size.FILL else value)
    }

    override fun body(content: View): View {
        val list = mutableListOf<Modified.Prop>()
        if (config.widthToken != null) list += Modified.prop(Properties.WIDTH, config.widthToken!!)
        else config.width?.let { list += Modified.prop(Properties.WIDTH, it) }
        if (config.heightToken != null) list += Modified.prop(Properties.HEIGHT, config.heightToken!!)
        else config.height?.let { list += Modified.prop(Properties.HEIGHT, it) }
        config.alignment?.let { list += Modified.prop(Properties.ALIGNMENT, it) }
        val props = intArrayOf(
            Properties.MIN_WIDTH, Properties.IDEAL_WIDTH, Properties.MAX_WIDTH,
            Properties.MIN_HEIGHT, Properties.IDEAL_HEIGHT, Properties.MAX_HEIGHT,
        )
        for (i in props.indices) config.bounds[i]?.let { list += Modified.prop(props[i], it) }
        return Modified.props(content, *list.toTypedArray())
    }

    companion object {
        fun of(width: Float? = null, height: Float? = null, alignment: Alignment? = null): Frame =
            Frame(Config().apply {
                if (width != null) width(width)
                if (height != null) height(height)
                if (alignment != null) alignment(alignment)
            })
        fun of(configure: Config.() -> Unit) = Frame(Config().apply(configure))
    }
}

class Border private constructor(private val config: Config) : ViewModifier {
    class Config {
        var color: Signal<Color>? = null
        var width: Signal<Float>? = null
        var widthToken: Length? = null
        var radius: Signal<Float>? = null
        var radiusToken: Length? = null
        fun color(value: Color) = apply { color = Signals.constant(value) }
        fun color(value: Signal<Color>) = apply { color = value }
        fun width(value: Float) = apply { width = Signals.constant(value); widthToken = null }
        fun width(value: Signal<Float>) = apply { width = value; widthToken = null }
        fun width(value: Length) = apply { widthToken = value; width = null }
        fun radius(value: Float) = apply { radius = Signals.constant(value); radiusToken = null }
        fun radius(value: Signal<Float>) = apply { radius = value; radiusToken = null }
        fun radius(value: Length) = apply { radiusToken = value; radius = null }
    }
    override fun body(content: View): View {
        val color = config.color ?: Signals.constant(Color.BLACK)
        val width = if (config.widthToken != null) Modified.prop(Properties.BORDER_WIDTH, config.widthToken!!)
        else Modified.prop(Properties.BORDER_WIDTH, config.width ?: Signals.constant(1f))
        if (config.radiusToken == null && config.radius == null) {
            return Modified.props(content, width, Modified.prop(Properties.BORDER_COLOR, color))
        }
        val radius = if (config.radiusToken != null) Modified.prop(Properties.BORDER_RADIUS, config.radiusToken!!)
        else Modified.prop(Properties.BORDER_RADIUS, config.radius!!)
        return Modified.props(content, width, Modified.prop(Properties.BORDER_COLOR, color), radius)
    }
    companion object {
        fun of(configure: Config.() -> Unit) = Border(Config().apply(configure))
    }
}

class Shadow private constructor(private val config: Config) : ViewModifier {
    class Config {
        var color: Signal<Color>? = null
        var radius: Signal<Float>? = null
        var x: Signal<Float>? = null
        var y: Signal<Float>? = null
        fun color(value: Color) = apply { color = Signals.constant(value) }
        fun color(value: Signal<Color>) = apply { color = value }
        fun radius(value: Float) = apply { radius = Signals.constant(value) }
        fun radius(value: Signal<Float>) = apply { radius = value }
        fun x(value: Float) = apply { x = Signals.constant(value) }
        fun y(value: Float) = apply { y = Signals.constant(value) }
    }
    override fun body(content: View): View {
        val radius = config.radius ?: Signals.constant(0f)
        if (config.color == null) return Modified.props(content, Modified.prop(Properties.SHADOW_RADIUS, radius))
        return Modified.props(
            content,
            Modified.prop(Properties.SHADOW_COLOR, config.color!!),
            Modified.prop(Properties.SHADOW_RADIUS, radius),
            Modified.prop(Properties.SHADOW_X, config.x ?: Signals.constant(0f)),
            Modified.prop(Properties.SHADOW_Y, config.y ?: Signals.constant(0f)),
        )
    }
    companion object {
        fun of(radius: Float) = Shadow(Config().radius(radius))
        fun of(configure: Config.() -> Unit) = Shadow(Config().apply(configure))
    }
}

class ClipShape private constructor(private val shape: Signal<ShapeKind>) : ViewModifier {
    override fun body(content: View): View = Modified.props(
        content,
        Modified.prop(Properties.CLIPS_TO_BOUNDS, 1),
        Modified.prop(Properties.SHAPE_KIND, shape),
    )
    companion object {
        fun of(shape: ShapeKind) = ClipShape(Signals.constant(shape))
        fun of(shape: Signal<ShapeKind>) = ClipShape(shape)
        fun of(configure: Config.() -> Unit) = ClipShape(Config().apply(configure).shape ?: Signals.constant(ShapeKind.RECTANGLE))
    }
    class Config {
        var shape: Signal<ShapeKind>? = null
        fun shape(value: ShapeKind) = apply { shape = Signals.constant(value) }
        fun shape(value: Signal<ShapeKind>) = apply { shape = value }
    }
}

/* Layout & frame */

fun View.padding(value: Float): View = modifiers(Padding.of(value))
fun View.padding(value: Int): View = padding(value.toFloat())
fun View.padding(value: Signal<Float>): View = modifiers(Padding.of(value))
fun View.padding(value: Length): View = modifiers(Padding.of(value))
fun View.padding(top: Float, right: Float = top, bottom: Float = top, left: Float = right): View =
    modifiers(Padding.of { edges(top, right, bottom, left) })
fun View.padding(top: Int, right: Int = top, bottom: Int = top, left: Int = right): View =
    padding(top.toFloat(), right.toFloat(), bottom.toFloat(), left.toFloat())
fun View.padding(configure: Padding.Config.() -> Unit): View = modifiers(Padding.of(configure))

fun View.frame(width: Float? = null, height: Float? = null, alignment: Alignment? = null): View =
    modifiers(Frame.of(width, height, alignment))
fun View.frame(width: Int, height: Int? = null, alignment: Alignment? = null): View =
    frame(width.toFloat(), height?.toFloat(), alignment)
fun View.frame(configure: Frame.Config.() -> Unit): View = modifiers(Frame.of(configure))

fun View.offset(x: Float = 0f, y: Float = 0f): View =
    modifiers(props(Modified.prop(Properties.OFFSET_X, x), Modified.prop(Properties.OFFSET_Y, y)))
fun View.offset(x: Int, y: Int = x): View = offset(x.toFloat(), y.toFloat())
fun View.position(x: Float, y: Float): View =
    modifiers(props(Modified.prop(Properties.POSITION_X, x), Modified.prop(Properties.POSITION_Y, y)))
fun View.position(x: Int, y: Int): View = position(x.toFloat(), y.toFloat())
fun View.fixedSize(horizontal: Boolean = true, vertical: Boolean = true): View =
    modifiers(props(
        Modified.prop(Properties.FIXED_SIZE_HORIZONTAL, if (horizontal) 1 else 0),
        Modified.prop(Properties.FIXED_SIZE_VERTICAL, if (vertical) 1 else 0),
    ))
fun View.layoutPriority(value: Float): View = modifiers(prop(Properties.LAYOUT_PRIORITY, value))
fun View.zIndex(value: Float): View = modifiers(prop(Properties.Z_INDEX, value))
fun View.aspectRatio(ratio: Float, contentMode: ContentMode = ContentMode.FIT): View =
    modifiers(props(
        Modified.prop(Properties.ASPECT_RATIO, ratio),
        Modified.prop(Properties.CONTENT_MODE, contentMode.wire().toFloat()),
    ))
fun View.scaledToFit(): View = modifiers(prop(Properties.CONTENT_MODE, ContentMode.FIT.wire().toFloat()))
fun View.scaledToFill(): View = modifiers(prop(Properties.CONTENT_MODE, ContentMode.FILL.wire().toFloat()))
fun View.minimumScaleFactor(value: Float): View = modifiers(prop(Properties.MINIMUM_SCALE_FACTOR, value))

/* Text formatting */

fun View.font(font: Font): View = modifiers(font)
fun View.fontSize(size: Float): View = modifiers(prop(Properties.FONT_SIZE, size))
fun View.fontSize(size: Int): View = fontSize(size.toFloat())
fun View.fontSize(size: Signal<Float>): View = modifiers(prop(Properties.FONT_SIZE, size))
fun View.fontSize(size: Length): View = modifiers(prop(Properties.FONT_SIZE, size))
fun View.fontWeight(weight: FontWeight): View = modifiers(prop(Properties.FONT_WEIGHT, weight.wire().toFloat()))
fun View.fontFamily(family: String): View = modifiers(prop(Properties.FONT_FAMILY, family))
fun View.fontFamily(family: Signal<String>): View = modifiers(prop(Properties.FONT_FAMILY, family))
fun View.fontStyle(style: FontStyle): View = modifiers(prop(Properties.FONT_STYLE, style.wire().toFloat()))
fun View.fontDesign(design: FontDesign): View = modifiers(prop(Properties.FONT_DESIGN, design.wire().toFloat()))
fun View.italic(): View = fontStyle(FontStyle.ITALIC)
fun View.fontWidth(value: Float): View = modifiers(prop(Properties.FONT_WIDTH, value))
fun View.fontWidth(value: Signal<Float>): View = modifiers(prop(Properties.FONT_WIDTH, value))
fun View.kerning(value: Float): View = modifiers(prop(Properties.KERNING, value))
fun View.tracking(value: Float): View = modifiers(prop(Properties.TRACKING, value))
fun View.baselineOffset(value: Float): View = modifiers(prop(Properties.BASELINE_OFFSET, value))
fun View.lineSpacing(value: Float): View = modifiers(prop(Properties.LINE_SPACING, value))
fun View.lineLimit(value: Int): View = modifiers(prop(Properties.LINE_LIMIT, value))
fun View.lineLimit(value: Signal<Int>): View = modifiers(prop(Properties.LINE_LIMIT, value))
fun View.multilineTextAlignment(alignment: TextAlignment): View =
    modifiers(prop(Properties.TEXT_ALIGNMENT, alignment.wire().toFloat()))
fun View.truncationMode(mode: Truncation): View =
    modifiers(prop(Properties.TRUNCATION_MODE, mode.wire().toFloat()))
fun View.textCase(textCase: TextCase): View =
    modifiers(prop(Properties.TEXT_CASE, textCase.wire().toFloat()))
fun View.underline(on: Boolean = true): View = modifiers(prop(Properties.UNDERLINE, if (on) 1 else 0))
fun View.strikethrough(on: Boolean = true): View = modifiers(prop(Properties.STRIKETHROUGH, if (on) 1 else 0))
fun View.foregroundStyle(color: Color): View = modifiers(prop(Properties.COLOR, color))
fun View.foregroundStyle(color: Signal<Color>): View = modifiers(prop(Properties.COLOR, color))
fun View.tint(color: Color): View = modifiers(prop(Properties.TINT, color))

/* Appearance & effects */

fun View.background(color: Color): View = modifiers(prop(Properties.BACKGROUND_COLOR, color))
fun View.background(color: Signal<Color>): View = modifiers(prop(Properties.BACKGROUND_COLOR, color))
fun View.border(color: Color, width: Float = 1f, radius: Float? = null): View =
    modifiers(Border.of { color(color).width(width); if (radius != null) radius(radius) })
fun View.border(configure: Border.Config.() -> Unit): View = modifiers(Border.of(configure))
fun View.cornerRadius(radius: Float): View = modifiers(prop(Properties.BORDER_RADIUS, radius))
fun View.cornerRadius(radius: Int): View = cornerRadius(radius.toFloat())
fun View.cornerRadius(radius: Signal<Float>): View = modifiers(prop(Properties.BORDER_RADIUS, radius))
fun View.cornerRadius(radius: Length): View = modifiers(prop(Properties.BORDER_RADIUS, radius))
fun View.shadow(radius: Float): View = modifiers(Shadow.of(radius))
fun View.shadow(color: Color? = null, radius: Float, x: Float = 0f, y: Float = 0f): View =
    modifiers(Shadow.of { if (color != null) color(color); radius(radius); x(x); y(y) })
fun View.shadow(configure: Shadow.Config.() -> Unit): View = modifiers(Shadow.of(configure))
fun View.opacity(value: Float): View = modifiers(prop(Properties.OPACITY, value))
fun View.opacity(value: Int): View = opacity(value.toFloat())
fun View.opacity(value: Signal<Float>): View = modifiers(prop(Properties.OPACITY, value))
fun View.blur(radius: Float): View = modifiers(prop(Properties.BLUR_RADIUS, radius))
fun View.blur(radius: Int): View = blur(radius.toFloat())
fun View.saturation(value: Float): View = modifiers(prop(Properties.SATURATION, value))
fun View.contrast(value: Float): View = modifiers(prop(Properties.CONTRAST, value))
fun View.brightness(value: Float): View = modifiers(prop(Properties.BRIGHTNESS, value))
fun View.grayscale(value: Float): View = modifiers(prop(Properties.GRAYSCALE, value))
fun View.hueRotation(degrees: Float): View = modifiers(prop(Properties.HUE_ROTATION, degrees))
fun View.colorMultiply(color: Color): View = modifiers(prop(Properties.COLOR_MULTIPLY, color))
fun View.colorInvert(): View = modifiers(prop(Properties.COLOR_INVERT, 1))
fun View.clipped(): View = modifiers(prop(Properties.CLIPS_TO_BOUNDS, 1))
fun View.clipShape(shape: ShapeKind): View = modifiers(ClipShape.of(shape))
fun View.clipShape(configure: ClipShape.Config.() -> Unit): View = modifiers(ClipShape.of(configure))

/* Transform */

fun View.rotationEffect(degrees: Float): View = modifiers(prop(Properties.ROTATION_DEGREES, degrees))
fun View.rotationEffect(degrees: Int): View = rotationEffect(degrees.toFloat())
fun View.rotation(degrees: Float): View = rotationEffect(degrees)
fun View.rotation(degrees: Int): View = rotationEffect(degrees.toFloat())
fun View.scaleEffect(value: Float): View = modifiers(prop(Properties.SCALE, value))
fun View.scaleEffect(value: Int): View = scaleEffect(value.toFloat())

/* Interaction & state */

fun View.hidden(): View = modifiers(prop(Properties.VISIBLE, 0))
fun View.visible(visible: Boolean): View = modifiers(prop(Properties.VISIBLE, if (visible) 1 else 0))
fun View.visible(visible: Signal<Boolean>): View = modifiers(prop(Properties.VISIBLE, visible))
fun View.disabled(disabled: Boolean = true): View = modifiers(prop(Properties.ENABLED, if (disabled) 0 else 1))
fun View.disabled(disabled: Signal<Boolean>): View = modifiers(prop(Properties.ENABLED, Signals.computed { if (disabled.get()) 0 else 1 }))
fun View.allowsHitTesting(allowed: Boolean = true): View = modifiers(prop(Properties.ALLOWS_HIT_TESTING, if (allowed) 1 else 0))
fun View.controlSize(size: ControlSize): View = modifiers(prop(Properties.CONTROL_SIZE, size.wire().toFloat()))
fun View.accessibilityLabel(label: String): View = modifiers(prop(Properties.LABEL, label))
fun View.accessibilityLabel(label: Signal<String>): View = modifiers(prop(Properties.LABEL, label))
fun View.accessibilityRole(role: Int): View = modifiers(prop(Properties.ROLE, role.toFloat()))
fun View.accessibilityState(state: Int): View = modifiers(prop(Properties.STATE, state.toFloat()))
fun View.onTapGesture(action: () -> Unit): View = modifiers(TapGesture.with(Runnable(action)))
fun View.pointerEvents(mask: Int): View = modifiers(PointerEvents.with(mask))
fun View.pathChange(listener: (String) -> Unit): View =
    modifiers(PathChange.with { c -> c.listener { listener(it) } })
fun View.buttonStyle(style: ButtonStyle): View = modifiers(style)
fun View.labelStyle(style: LabelStyle): View = modifiers(style)
fun View.videoStyle(style: VideoStyle): View = modifiers(style)
fun View.windowMetadata(configure: WindowMetadata.Config.() -> Unit): View =
    modifiers(WindowMetadata.with { c -> c.configure() })
fun <T> View.environment(key: EnvironmentKey<T>, value: T): View =
    modifiers(EnvironmentBinding.with { c -> c.key(key).value(value) })
fun <T> View.environment(key: EnvironmentKey<T>, value: Signal<T>): View =
    modifiers(EnvironmentBinding.with { c -> c.key(key).value(value) })
