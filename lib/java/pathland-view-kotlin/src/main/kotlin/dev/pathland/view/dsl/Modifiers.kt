package dev.pathland.view.dsl

import dev.pathland.view.Border
import dev.pathland.view.core.ButtonStyle
import dev.pathland.view.ClipShape
import dev.pathland.view.core.Color as JColor
import dev.pathland.view.core.ContentMode
import dev.pathland.view.core.ControlSize
import dev.pathland.view.core.EnvironmentKey
import dev.pathland.view.Font
import dev.pathland.view.core.FontDesign
import dev.pathland.view.core.FontStyle
import dev.pathland.view.core.FontWeight
import dev.pathland.view.Frame
import dev.pathland.view.core.LabelStyle
import dev.pathland.view.core.Length as JLength
import dev.pathland.view.Modifiers as M
import dev.pathland.view.Padding
import dev.pathland.view.Shadow
import dev.pathland.view.core.ShapeKind
import dev.pathland.view.core.TextAlignment
import dev.pathland.view.core.TextCase
import dev.pathland.view.core.Truncation
import dev.pathland.view.core.VideoStyle
import dev.pathland.view.core.View
import dev.pathland.view.core.WindowMetadata
import dev.pathland.view.core.signal.Signal

/* ------------------------------------------------------------------------- */
/* Layout & frame                                                             */
/* ------------------------------------------------------------------------- */

fun View.padding(value: Float): View = modifiers(M.padding(value))

fun View.padding(value: Int): View = padding(value.toFloat())

fun View.padding(value: Signal<Float>): View = modifiers(M.padding(value))

fun View.padding(value: JLength): View = modifiers(M.padding(value))

fun View.padding(top: Float, right: Float = top, bottom: Float = top, left: Float = right): View =
    modifiers(M.padding { c -> c.edges(top, right, bottom, left) })

fun View.padding(top: Int, right: Int = top, bottom: Int = top, left: Int = right): View =
    padding(top.toFloat(), right.toFloat(), bottom.toFloat(), left.toFloat())

fun View.padding(configure: Padding.Config.() -> Unit): View =
    modifiers(M.padding { c -> c.configure() })

fun View.frame(
    width: Float? = null,
    height: Float? = null,
    alignment: dev.pathland.view.core.Alignment? = null,
): View = modifiers(M.frame { c ->
    if (width != null) c.width(width)
    if (height != null) c.height(height)
    if (alignment != null) c.alignment(alignment)
})

fun View.frame(
    width: Int,
    height: Int? = null,
    alignment: dev.pathland.view.core.Alignment? = null,
): View = frame(width.toFloat(), height?.toFloat(), alignment)

fun View.frame(configure: Frame.Config.() -> Unit): View =
    modifiers(M.frame { c -> c.configure() })

fun View.offset(x: Float = 0f, y: Float = 0f): View =
    modifiers(M.offset { c -> c.x(x).y(y) })

fun View.offset(x: Int, y: Int = x): View = offset(x.toFloat(), y.toFloat())

fun View.position(x: Float, y: Float): View =
    modifiers(M.position { c -> c.x(x).y(y) })

fun View.position(x: Int, y: Int): View = position(x.toFloat(), y.toFloat())

fun View.fixedSize(horizontal: Boolean = true, vertical: Boolean = true): View =
    modifiers(M.fixedSize { c -> c.horizontal(horizontal).vertical(vertical) })

fun View.layoutPriority(value: Float): View = modifiers(M.layoutPriority(value))

fun View.zIndex(value: Float): View = modifiers(M.zIndex(value))

fun View.aspectRatio(ratio: Float, contentMode: ContentMode = ContentMode.FIT): View =
    modifiers(M.aspectRatio { c -> c.ratio(ratio).contentMode(contentMode) })

fun View.scaledToFit(): View = modifiers(M.scaledToFit())

fun View.scaledToFill(): View = modifiers(M.scaledToFill())

fun View.minimumScaleFactor(value: Float): View = modifiers(M.minimumScaleFactor(value))

/* ------------------------------------------------------------------------- */
/* Text formatting                                                            */
/* ------------------------------------------------------------------------- */

fun View.font(font: Font): View = modifiers(font)

fun View.fontSize(size: Float): View = modifiers(M.fontSize(size))

fun View.fontSize(size: Int): View = fontSize(size.toFloat())

fun View.fontSize(size: Signal<Float>): View = modifiers(M.fontSize(size))

fun View.fontSize(size: JLength): View = modifiers(M.fontSize(size))

fun View.fontWeight(weight: FontWeight): View = modifiers(weight)

fun View.fontFamily(family: String): View = modifiers(M.fontFamily(family))

fun View.fontFamily(family: Signal<String>): View = modifiers(M.fontFamily(family))

fun View.fontStyle(style: FontStyle): View = modifiers(style)

fun View.fontDesign(design: FontDesign): View = modifiers(design)

fun View.italic(): View = modifiers(M.italic())

fun View.fontWidth(value: Float): View = modifiers(M.fontWidth(value))

fun View.fontWidth(value: Signal<Float>): View = modifiers(M.fontWidth(value))

fun View.kerning(value: Float): View = modifiers(M.kerning(value))

fun View.tracking(value: Float): View = modifiers(M.tracking(value))

fun View.baselineOffset(value: Float): View = modifiers(M.baselineOffset(value))

fun View.lineSpacing(value: Float): View = modifiers(M.lineSpacing(value))

fun View.lineLimit(value: Int): View = modifiers(M.lineLimit(value))

fun View.lineLimit(value: Signal<Int>): View = modifiers(M.lineLimit(value))

fun View.multilineTextAlignment(alignment: TextAlignment): View = modifiers(alignment)

fun View.truncationMode(mode: Truncation): View = modifiers(mode)

fun View.textCase(textCase: TextCase): View = modifiers(textCase)

fun View.underline(on: Boolean = true): View = modifiers(M.underline(on))

fun View.strikethrough(on: Boolean = true): View = modifiers(M.strikethrough(on))

fun View.foregroundStyle(color: JColor): View = modifiers(M.foregroundStyle(color))

fun View.foregroundStyle(color: Signal<JColor>): View = modifiers(M.foregroundStyle(color))

fun View.tint(color: JColor): View = modifiers(M.tint(color))

/* ------------------------------------------------------------------------- */
/* Appearance & effects                                                       */
/* ------------------------------------------------------------------------- */

fun View.background(color: JColor): View = modifiers(M.background(color))

fun View.background(color: Signal<JColor>): View = modifiers(M.background(color))

fun View.border(color: JColor, width: Float = 1f, radius: Float? = null): View =
    modifiers(M.border { c ->
        c.color(color).width(width)
        if (radius != null) c.radius(radius)
    })

fun View.border(configure: Border.Config.() -> Unit): View =
    modifiers(M.border { c -> c.configure() })

fun View.cornerRadius(radius: Float): View = modifiers(M.cornerRadius(radius))

fun View.cornerRadius(radius: Int): View = cornerRadius(radius.toFloat())

fun View.cornerRadius(radius: Signal<Float>): View = modifiers(M.cornerRadius(radius))

fun View.cornerRadius(radius: JLength): View = modifiers(M.cornerRadius(radius))

fun View.shadow(radius: Float): View = modifiers(M.shadow(radius))

fun View.shadow(
    color: JColor? = null,
    radius: Float,
    x: Float = 0f,
    y: Float = 0f,
): View = modifiers(M.shadow { c ->
    if (color != null) c.color(color)
    c.radius(radius).x(x).y(y)
})

fun View.shadow(configure: Shadow.Config.() -> Unit): View =
    modifiers(M.shadow { c -> c.configure() })

fun View.opacity(value: Float): View = modifiers(M.opacity(value))

fun View.opacity(value: Int): View = opacity(value.toFloat())

fun View.opacity(value: Signal<Float>): View = modifiers(M.opacity(value))

fun View.blur(radius: Float): View = modifiers(M.blur(radius))

fun View.blur(radius: Int): View = blur(radius.toFloat())

fun View.saturation(value: Float): View = modifiers(M.saturation(value))

fun View.contrast(value: Float): View = modifiers(M.contrast(value))

fun View.brightness(value: Float): View = modifiers(M.brightness(value))

fun View.grayscale(value: Float): View = modifiers(M.grayscale(value))

fun View.hueRotation(degrees: Float): View = modifiers(M.hueRotation(degrees))

fun View.colorMultiply(color: JColor): View = modifiers(M.colorMultiply(color))

fun View.colorInvert(): View = modifiers(M.colorInvert())

fun View.clipped(): View = modifiers(M.clipped())

fun View.clipShape(shape: ShapeKind): View = modifiers(M.clipShape(shape))

fun View.clipShape(configure: ClipShape.Config.() -> Unit): View =
    modifiers(M.clipShape { c -> c.configure() })

/* ------------------------------------------------------------------------- */
/* Transform                                                                  */
/* ------------------------------------------------------------------------- */

fun View.rotationEffect(degrees: Float): View = modifiers(M.rotation(degrees))

fun View.rotationEffect(degrees: Int): View = rotationEffect(degrees.toFloat())

fun View.rotation(degrees: Float): View = rotationEffect(degrees)

fun View.rotation(degrees: Int): View = rotationEffect(degrees.toFloat())

fun View.scaleEffect(value: Float): View = modifiers(M.scaleEffect(value))

fun View.scaleEffect(value: Int): View = scaleEffect(value.toFloat())

/* ------------------------------------------------------------------------- */
/* Interaction & state                                                        */
/* ------------------------------------------------------------------------- */

fun View.hidden(): View = modifiers(M.hidden())

fun View.visible(visible: Boolean): View = modifiers(M.visible(visible))

fun View.visible(visible: Signal<Boolean>): View = modifiers(M.visible(visible))

fun View.disabled(disabled: Boolean = true): View = modifiers(M.disabled(disabled))

fun View.disabled(disabled: Signal<Boolean>): View = modifiers(M.disabled(disabled))

fun View.allowsHitTesting(allowed: Boolean = true): View = modifiers(M.allowsHitTesting(allowed))

fun View.controlSize(size: ControlSize): View = modifiers(size)

fun View.accessibilityLabel(label: String): View = modifiers(M.accessibilityLabel(label))

fun View.accessibilityLabel(label: Signal<String>): View = modifiers(M.accessibilityLabel(label))

fun View.accessibilityRole(role: Int): View = modifiers(M.accessibilityRole(role))

fun View.accessibilityState(state: Int): View = modifiers(M.accessibilityState(state))

fun View.onTapGesture(action: () -> Unit): View = modifiers(M.tapGesture(action))

fun View.pointerEvents(mask: Int): View = modifiers(M.pointerEvents(mask))

fun View.pathChange(listener: (String) -> Unit): View =
    modifiers(M.pathChange { c -> c.listener { listener(it) } })

fun View.buttonStyle(style: ButtonStyle): View = modifiers(style)

fun View.labelStyle(style: LabelStyle): View = modifiers(style)

fun View.videoStyle(style: VideoStyle): View = modifiers(style)

fun View.windowMetadata(configure: WindowMetadata.Config.() -> Unit): View =
    modifiers(M.windowMetadata { c -> c.configure() })

fun <T> View.environment(key: EnvironmentKey<T>, value: T): View =
    modifiers(M.environmentBinding { c -> c.key(key).value(value) })

fun <T> View.environment(key: EnvironmentKey<T>, value: Signal<T>): View =
    modifiers(M.environmentBinding { c -> c.key(key).value(value) })
