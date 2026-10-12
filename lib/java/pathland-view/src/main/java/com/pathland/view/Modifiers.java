package com.pathland.view;

import com.pathland.view.signal.Signal;

import java.util.function.Consumer;

/**
 * The single construction surface for Pathland modifiers and styles.
 *
 * <p>Every parameterized modifier (and style) in the library is built through
 * a lowercase static function here — {@code padding(16)}, {@code frame(f -> ...)},
 * {@code borderedButtonStyle(b -> ...)} — so one {@code import static
 * com.pathland.view.Modifiers.*;} brings the whole modifier vocabulary into a file
 * (spec DSL.md §5.0). These functions mirror the construction path of the
 * {@code X.with(...)} configurators they replace: each returns the modifier/style
 * value ready for {@code View.modifiers(...)}. Views construct with {@code make(...)};
 * only modifiers and styles live here.
 *
 * <p>Application-authored modifiers (a custom {@link ViewModifier} or a custom style)
 * expose a lowercase static factory on their own type, e.g.
 * {@code public static PlayerControlsStyle playerControlsStyle(Consumer<Config> c)}.
 */
public final class Modifiers {

    private Modifiers() {
    }

    /* ---- accessibilityLabel -------------------------------------------------------------- */

    /** A AccessibilityLabel value (accessibilityLabel(...)). */
    public static AccessibilityLabel accessibilityLabel(String value) {
        return AccessibilityLabel.with(value);
    }

    /** A AccessibilityLabel value (accessibilityLabel(...)). */
    public static AccessibilityLabel accessibilityLabel(Signal<String> signal) {
        return AccessibilityLabel.with(signal);
    }

    /** A AccessibilityLabel value (accessibilityLabel(...)). */
    public static AccessibilityLabel accessibilityLabel(Consumer<AccessibilityLabel.Config> configure) {
        return AccessibilityLabel.with(configure);
    }

    /* ---- accessibilityRole -------------------------------------------------------------- */

    /** A AccessibilityRole value (accessibilityRole(...)). */
    public static AccessibilityRole accessibilityRole(int value) {
        return AccessibilityRole.with(value);
    }

    /** A AccessibilityRole value (accessibilityRole(...)). */
    public static AccessibilityRole accessibilityRole(Signal<Integer> role) {
        return AccessibilityRole.with(role);
    }

    /** A AccessibilityRole value (accessibilityRole(...)). */
    public static AccessibilityRole accessibilityRole(Consumer<AccessibilityRole.Config> configure) {
        return AccessibilityRole.with(configure);
    }

    /* ---- accessibilityState -------------------------------------------------------------- */

    /** A AccessibilityState value (accessibilityState(...)). */
    public static AccessibilityState accessibilityState(int value) {
        return AccessibilityState.with(value);
    }

    /** A AccessibilityState value (accessibilityState(...)). */
    public static AccessibilityState accessibilityState(Signal<Integer> state) {
        return AccessibilityState.with(state);
    }

    /** A AccessibilityState value (accessibilityState(...)). */
    public static AccessibilityState accessibilityState(Consumer<AccessibilityState.Config> configure) {
        return AccessibilityState.with(configure);
    }

    /* ---- actionId -------------------------------------------------------------- */

    /** A ActionId value (actionId(...)). */
    public static ActionId actionId(Consumer<ActionId.Config> configure) {
        return ActionId.with(configure);
    }

    /* ---- allowsHitTesting -------------------------------------------------------------- */

    /** A AllowsHitTesting value (allowsHitTesting(...)). */
    public static AllowsHitTesting allowsHitTesting(boolean on) {
        return AllowsHitTesting.with(on);
    }

    /** A AllowsHitTesting value (allowsHitTesting(...)). */
    public static AllowsHitTesting allowsHitTesting(Signal<Boolean> on) {
        return AllowsHitTesting.with(on);
    }

    /** A AllowsHitTesting value (allowsHitTesting(...)). */
    public static AllowsHitTesting allowsHitTesting(Consumer<AllowsHitTesting.Config> configure) {
        return AllowsHitTesting.with(configure);
    }

    /* ---- aspectRatio -------------------------------------------------------------- */

    /** A AspectRatio value (aspectRatio(...)). */
    public static AspectRatio aspectRatio(Consumer<AspectRatio.Config> configure) {
        return AspectRatio.with(configure);
    }

    /* ---- background -------------------------------------------------------------- */

    /** A Background value (background(...)). */
    public static Background background(Color value) {
        return Background.with(value);
    }

    /** A Background value (background(...)). */
    public static Background background(Signal<Color> signal) {
        return Background.with(signal);
    }

    /** A Background value (background(...)). */
    public static Background background(Consumer<Background.Config> configure) {
        return Background.with(configure);
    }

    /* ---- baselineOffset -------------------------------------------------------------- */

    /** A BaselineOffset value (baselineOffset(...)). */
    public static BaselineOffset baselineOffset(float value) {
        return BaselineOffset.with(value);
    }

    /** A BaselineOffset value (baselineOffset(...)). */
    public static BaselineOffset baselineOffset(Signal<Float> value) {
        return BaselineOffset.with(value);
    }

    /** A BaselineOffset value (baselineOffset(...)). */
    public static BaselineOffset baselineOffset(Consumer<BaselineOffset.Config> configure) {
        return BaselineOffset.with(configure);
    }

    /* ---- bindingId -------------------------------------------------------------- */

    /** A BindingId value (bindingId(...)). */
    public static BindingId bindingId(Consumer<BindingId.Config> configure) {
        return BindingId.with(configure);
    }

    /* ---- blur -------------------------------------------------------------- */

    /** A Blur value (blur(...)). */
    public static Blur blur(float value) {
        return Blur.with(value);
    }

    /** A Blur value (blur(...)). */
    public static Blur blur(Signal<Float> value) {
        return Blur.with(value);
    }

    /** A Blur value (blur(...)). */
    public static Blur blur(Consumer<Blur.Config> configure) {
        return Blur.with(configure);
    }

    /* ---- border -------------------------------------------------------------- */

    /** A Border value (border(...)). */
    public static Border border(Consumer<Border.Config> configure) {
        return Border.with(configure);
    }

    /* ---- borderedButtonStyle -------------------------------------------------------------- */

    /** A BorderedButtonStyle value (borderedButtonStyle(...)). */
    public static BorderedButtonStyle borderedButtonStyle(Consumer<BorderedButtonStyle.Config> configure) {
        return BorderedButtonStyle.with(configure);
    }

    /* ---- brightness -------------------------------------------------------------- */

    /** A Brightness value (brightness(...)). */
    public static Brightness brightness(float value) {
        return Brightness.with(value);
    }

    /** A Brightness value (brightness(...)). */
    public static Brightness brightness(Signal<Float> value) {
        return Brightness.with(value);
    }

    /** A Brightness value (brightness(...)). */
    public static Brightness brightness(Consumer<Brightness.Config> configure) {
        return Brightness.with(configure);
    }

    /* ---- clipShape -------------------------------------------------------------- */

    /** A ClipShape value (clipShape(...)). */
    public static ClipShape clipShape(ShapeKind kind) {
        return ClipShape.with(kind);
    }

    /** A ClipShape value (clipShape(...)). */
    public static ClipShape clipShape(Signal<ShapeKind> kind) {
        return ClipShape.with(kind);
    }

    /** A ClipShape value (clipShape(...)). */
    public static ClipShape clipShape(Consumer<ClipShape.Config> configure) {
        return ClipShape.with(configure);
    }

    /* ---- clipped -------------------------------------------------------------- */

    /** A Clipped value (clipped(...)). */
    public static Clipped clipped() {
        return Clipped.with();
    }

    /* ---- colorInvert -------------------------------------------------------------- */

    /** A ColorInvert value (colorInvert(...)). */
    public static ColorInvert colorInvert() {
        return ColorInvert.with();
    }

    /* ---- colorMultiply -------------------------------------------------------------- */

    /** A ColorMultiply value (colorMultiply(...)). */
    public static ColorMultiply colorMultiply(Color color) {
        return ColorMultiply.with(color);
    }

    /** A ColorMultiply value (colorMultiply(...)). */
    public static ColorMultiply colorMultiply(Signal<Color> color) {
        return ColorMultiply.with(color);
    }

    /** A ColorMultiply value (colorMultiply(...)). */
    public static ColorMultiply colorMultiply(Consumer<ColorMultiply.Config> configure) {
        return ColorMultiply.with(configure);
    }

    /* ---- contrast -------------------------------------------------------------- */

    /** A Contrast value (contrast(...)). */
    public static Contrast contrast(float value) {
        return Contrast.with(value);
    }

    /** A Contrast value (contrast(...)). */
    public static Contrast contrast(Signal<Float> value) {
        return Contrast.with(value);
    }

    /** A Contrast value (contrast(...)). */
    public static Contrast contrast(Consumer<Contrast.Config> configure) {
        return Contrast.with(configure);
    }

    /* ---- cornerRadius -------------------------------------------------------------- */

    /** A CornerRadius value (cornerRadius(...)). */
    public static CornerRadius cornerRadius(float value) {
        return CornerRadius.with(value);
    }

    /** A CornerRadius value (cornerRadius(...)). */
    public static CornerRadius cornerRadius(Signal<Float> value) {
        return CornerRadius.with(value);
    }

    /** A CornerRadius value (cornerRadius(...)). */
    public static CornerRadius cornerRadius(Consumer<CornerRadius.Config> configure) {
        return CornerRadius.with(configure);
    }

    /* ---- disabled -------------------------------------------------------------- */

    /** A Disabled value (disabled(...)). */
    public static Disabled disabled(boolean disabled) {
        return Disabled.with(disabled);
    }

    /** A Disabled value (disabled(...)). */
    public static Disabled disabled(Signal<Boolean> disabled) {
        return Disabled.with(disabled);
    }

    /** A Disabled value (disabled(...)). */
    public static Disabled disabled(Consumer<Disabled.Config> configure) {
        return Disabled.with(configure);
    }

    /* ---- environmentBinding -------------------------------------------------------------- */

    /** A EnvironmentBinding value (environmentBinding(...)). */
    public static EnvironmentBinding environmentBinding(Consumer<EnvironmentBinding.Config> configure) {
        return EnvironmentBinding.with(configure);
    }

    /* ---- fixedSize -------------------------------------------------------------- */

    /** A FixedSize value (fixedSize(...)). */
    public static FixedSize fixedSize() {
        return FixedSize.with();
    }

    /** A FixedSize value (fixedSize(...)). */
    public static FixedSize fixedSize(Consumer<FixedSize.Config> configure) {
        return FixedSize.with(configure);
    }

    /* ---- fontFamily -------------------------------------------------------------- */

    /** A FontFamily value (fontFamily(...)). */
    public static FontFamily fontFamily(String value) {
        return FontFamily.with(value);
    }

    /** A FontFamily value (fontFamily(...)). */
    public static FontFamily fontFamily(Signal<String> value) {
        return FontFamily.with(value);
    }

    /** A FontFamily value (fontFamily(...)). */
    public static FontFamily fontFamily(Consumer<FontFamily.Config> configure) {
        return FontFamily.with(configure);
    }

    /* ---- fontSize -------------------------------------------------------------- */

    /** A FontSize value (fontSize(...)). */
    public static FontSize fontSize(float size) {
        return FontSize.with(size);
    }

    /** A FontSize value (fontSize(...)). */
    public static FontSize fontSize(Signal<Float> signal) {
        return FontSize.with(signal);
    }

    /** A FontSize value (fontSize(...)). */
    public static FontSize fontSize(Consumer<FontSize.Config> configure) {
        return FontSize.with(configure);
    }

    /* ---- fontWidth -------------------------------------------------------------- */

    /** A FontWidth value (fontWidth(...)). */
    public static FontWidth fontWidth(float value) {
        return FontWidth.with(value);
    }

    /** A FontWidth value (fontWidth(...)). */
    public static FontWidth fontWidth(Signal<Float> value) {
        return FontWidth.with(value);
    }

    /** A FontWidth value (fontWidth(...)). */
    public static FontWidth fontWidth(Consumer<FontWidth.Config> configure) {
        return FontWidth.with(configure);
    }

    /* ---- foregroundStyle -------------------------------------------------------------- */

    /** A ForegroundStyle value (foregroundStyle(...)). */
    public static ForegroundStyle foregroundStyle(Color value) {
        return ForegroundStyle.with(value);
    }

    /** A ForegroundStyle value (foregroundStyle(...)). */
    public static ForegroundStyle foregroundStyle(Signal<Color> signal) {
        return ForegroundStyle.with(signal);
    }

    /** A ForegroundStyle value (foregroundStyle(...)). */
    public static ForegroundStyle foregroundStyle(Consumer<ForegroundStyle.Config> configure) {
        return ForegroundStyle.with(configure);
    }

    /* ---- frame -------------------------------------------------------------- */

    /** A Frame value (frame(...)). */
    public static Frame frame(Consumer<Frame.Config> configure) {
        return Frame.with(configure);
    }

    /* ---- grayscale -------------------------------------------------------------- */

    /** A Grayscale value (grayscale(...)). */
    public static Grayscale grayscale(float value) {
        return Grayscale.with(value);
    }

    /** A Grayscale value (grayscale(...)). */
    public static Grayscale grayscale(Signal<Float> value) {
        return Grayscale.with(value);
    }

    /** A Grayscale value (grayscale(...)). */
    public static Grayscale grayscale(Consumer<Grayscale.Config> configure) {
        return Grayscale.with(configure);
    }

    /* ---- hidden -------------------------------------------------------------- */

    /** A Hidden value (hidden(...)). */
    public static Hidden hidden() {
        return Hidden.with();
    }

    /* ---- hueRotation -------------------------------------------------------------- */

    /** A HueRotation value (hueRotation(...)). */
    public static HueRotation hueRotation(float value) {
        return HueRotation.with(value);
    }

    /** A HueRotation value (hueRotation(...)). */
    public static HueRotation hueRotation(Signal<Float> value) {
        return HueRotation.with(value);
    }

    /** A HueRotation value (hueRotation(...)). */
    public static HueRotation hueRotation(Consumer<HueRotation.Config> configure) {
        return HueRotation.with(configure);
    }

    /* ---- imageSource -------------------------------------------------------------- */

    /** A ImageSource value (imageSource(...)). */
    public static ImageSource imageSource(String value) {
        return ImageSource.with(value);
    }

    /** A ImageSource value (imageSource(...)). */
    public static ImageSource imageSource(Signal<String> value) {
        return ImageSource.with(value);
    }

    /** A ImageSource value (imageSource(...)). */
    public static ImageSource imageSource(Consumer<ImageSource.Config> configure) {
        return ImageSource.with(configure);
    }

    /* ---- italic -------------------------------------------------------------- */

    /** A Italic value (italic(...)). */
    public static Italic italic() {
        return Italic.with();
    }

    /* ---- kerning -------------------------------------------------------------- */

    /** A Kerning value (kerning(...)). */
    public static Kerning kerning(float value) {
        return Kerning.with(value);
    }

    /** A Kerning value (kerning(...)). */
    public static Kerning kerning(Signal<Float> value) {
        return Kerning.with(value);
    }

    /** A Kerning value (kerning(...)). */
    public static Kerning kerning(Consumer<Kerning.Config> configure) {
        return Kerning.with(configure);
    }

    /* ---- layoutPriority -------------------------------------------------------------- */

    /** A LayoutPriority value (layoutPriority(...)). */
    public static LayoutPriority layoutPriority(float value) {
        return LayoutPriority.with(value);
    }

    /** A LayoutPriority value (layoutPriority(...)). */
    public static LayoutPriority layoutPriority(Signal<Float> value) {
        return LayoutPriority.with(value);
    }

    /** A LayoutPriority value (layoutPriority(...)). */
    public static LayoutPriority layoutPriority(Consumer<LayoutPriority.Config> configure) {
        return LayoutPriority.with(configure);
    }

    /* ---- lineLimit -------------------------------------------------------------- */

    /** A LineLimit value (lineLimit(...)). */
    public static LineLimit lineLimit(int lines) {
        return LineLimit.with(lines);
    }

    /** A LineLimit value (lineLimit(...)). */
    public static LineLimit lineLimit(Signal<Integer> value) {
        return LineLimit.with(value);
    }

    /** A LineLimit value (lineLimit(...)). */
    public static LineLimit lineLimit(Consumer<LineLimit.Config> configure) {
        return LineLimit.with(configure);
    }

    /* ---- lineSpacing -------------------------------------------------------------- */

    /** A LineSpacing value (lineSpacing(...)). */
    public static LineSpacing lineSpacing(float value) {
        return LineSpacing.with(value);
    }

    /** A LineSpacing value (lineSpacing(...)). */
    public static LineSpacing lineSpacing(Signal<Float> value) {
        return LineSpacing.with(value);
    }

    /** A LineSpacing value (lineSpacing(...)). */
    public static LineSpacing lineSpacing(Consumer<LineSpacing.Config> configure) {
        return LineSpacing.with(configure);
    }

    /* ---- minimumScaleFactor -------------------------------------------------------------- */

    /** A MinimumScaleFactor value (minimumScaleFactor(...)). */
    public static MinimumScaleFactor minimumScaleFactor(float value) {
        return MinimumScaleFactor.with(value);
    }

    /** A MinimumScaleFactor value (minimumScaleFactor(...)). */
    public static MinimumScaleFactor minimumScaleFactor(Signal<Float> value) {
        return MinimumScaleFactor.with(value);
    }

    /** A MinimumScaleFactor value (minimumScaleFactor(...)). */
    public static MinimumScaleFactor minimumScaleFactor(Consumer<MinimumScaleFactor.Config> configure) {
        return MinimumScaleFactor.with(configure);
    }

    /* ---- offset -------------------------------------------------------------- */

    /** A Offset value (offset(...)). */
    public static Offset offset(Consumer<Offset.Config> configure) {
        return Offset.with(configure);
    }

    /* ---- opacity -------------------------------------------------------------- */

    /** A Opacity value (opacity(...)). */
    public static Opacity opacity(float value) {
        return Opacity.with(value);
    }

    /** A Opacity value (opacity(...)). */
    public static Opacity opacity(Signal<Float> value) {
        return Opacity.with(value);
    }

    /** A Opacity value (opacity(...)). */
    public static Opacity opacity(Consumer<Opacity.Config> configure) {
        return Opacity.with(configure);
    }

    /* ---- padding -------------------------------------------------------------- */

    /** A Padding value (padding(...)). */
    public static Padding padding(float value) {
        return Padding.with(value);
    }

    /** A Padding value (padding(...)). */
    public static Padding padding(Signal<Float> value) {
        return Padding.with(value);
    }

    /** A Padding value (padding(...)). */
    public static Padding padding(Consumer<Padding.Config> configure) {
        return Padding.with(configure);
    }

    /* ---- pathChange -------------------------------------------------------------- */

    /** A PathChange value (pathChange(...)). */
    public static PathChange pathChange(Consumer<PathChange.Config> configure) {
        return PathChange.with(configure);
    }

    /* ---- pointerEvents -------------------------------------------------------------- */

    /** A PointerEvents value (pointerEvents(...)). */
    public static PointerEvents pointerEvents(int mask) {
        return PointerEvents.with(mask);
    }

    /** A PointerEvents value (pointerEvents(...)). */
    public static PointerEvents pointerEvents(Consumer<PointerEvents.Config> configure) {
        return PointerEvents.with(configure);
    }

    /* ---- position -------------------------------------------------------------- */

    /** A Position value (position(...)). */
    public static Position position(Consumer<Position.Config> configure) {
        return Position.with(configure);
    }

    /* ---- rotation -------------------------------------------------------------- */

    /** A Rotation value (rotation(...)). */
    public static Rotation rotation(float value) {
        return Rotation.with(value);
    }

    /** A Rotation value (rotation(...)). */
    public static Rotation rotation(Signal<Float> value) {
        return Rotation.with(value);
    }

    /** A Rotation value (rotation(...)). */
    public static Rotation rotation(Consumer<Rotation.Config> configure) {
        return Rotation.with(configure);
    }

    /* ---- saturation -------------------------------------------------------------- */

    /** A Saturation value (saturation(...)). */
    public static Saturation saturation(float value) {
        return Saturation.with(value);
    }

    /** A Saturation value (saturation(...)). */
    public static Saturation saturation(Signal<Float> value) {
        return Saturation.with(value);
    }

    /** A Saturation value (saturation(...)). */
    public static Saturation saturation(Consumer<Saturation.Config> configure) {
        return Saturation.with(configure);
    }

    /* ---- scaleEffect -------------------------------------------------------------- */

    /** A ScaleEffect value (scaleEffect(...)). */
    public static ScaleEffect scaleEffect(float value) {
        return ScaleEffect.with(value);
    }

    /** A ScaleEffect value (scaleEffect(...)). */
    public static ScaleEffect scaleEffect(Signal<Float> value) {
        return ScaleEffect.with(value);
    }

    /** A ScaleEffect value (scaleEffect(...)). */
    public static ScaleEffect scaleEffect(Consumer<ScaleEffect.Config> configure) {
        return ScaleEffect.with(configure);
    }

    /* ---- scaledToFill -------------------------------------------------------------- */

    /** A ScaledToFill value (scaledToFill(...)). */
    public static ScaledToFill scaledToFill() {
        return ScaledToFill.with();
    }

    /* ---- scaledToFit -------------------------------------------------------------- */

    /** A ScaledToFit value (scaledToFit(...)). */
    public static ScaledToFit scaledToFit() {
        return ScaledToFit.with();
    }

    /* ---- shadow -------------------------------------------------------------- */

    /** A Shadow value (shadow(...)). */
    public static Shadow shadow(float radius) {
        return Shadow.with(radius);
    }

    /** A Shadow value (shadow(...)). */
    public static Shadow shadow(Signal<Float> radius) {
        return Shadow.with(radius);
    }

    /** A Shadow value (shadow(...)). */
    public static Shadow shadow(Consumer<Shadow.Config> configure) {
        return Shadow.with(configure);
    }

    /* ---- strikethrough -------------------------------------------------------------- */

    /** A Strikethrough value (strikethrough(...)). */
    public static Strikethrough strikethrough(boolean on) {
        return Strikethrough.with(on);
    }

    /** A Strikethrough value (strikethrough(...)). */
    public static Strikethrough strikethrough(Signal<Boolean> on) {
        return Strikethrough.with(on);
    }

    /** A Strikethrough value (strikethrough(...)). */
    public static Strikethrough strikethrough() {
        return Strikethrough.with();
    }

    /** A Strikethrough value (strikethrough(...)). */
    public static Strikethrough strikethrough(Consumer<Strikethrough.Config> configure) {
        return Strikethrough.with(configure);
    }

    /* ---- tapGesture -------------------------------------------------------------- */

    /** A TapGesture value (tapGesture(...)). */
    public static TapGesture tapGesture(Runnable action) {
        return TapGesture.with(action);
    }

    /** A TapGesture value (tapGesture(...)). */
    public static TapGesture tapGesture(Consumer<TapGesture.Config> configure) {
        return TapGesture.with(configure);
    }

    /* ---- tint -------------------------------------------------------------- */

    /** A Tint value (tint(...)). */
    public static Tint tint(Color color) {
        return Tint.with(color);
    }

    /** A Tint value (tint(...)). */
    public static Tint tint(Signal<Color> color) {
        return Tint.with(color);
    }

    /** A Tint value (tint(...)). */
    public static Tint tint(Consumer<Tint.Config> configure) {
        return Tint.with(configure);
    }

    /* ---- tracking -------------------------------------------------------------- */

    /** A Tracking value (tracking(...)). */
    public static Tracking tracking(float value) {
        return Tracking.with(value);
    }

    /** A Tracking value (tracking(...)). */
    public static Tracking tracking(Signal<Float> value) {
        return Tracking.with(value);
    }

    /** A Tracking value (tracking(...)). */
    public static Tracking tracking(Consumer<Tracking.Config> configure) {
        return Tracking.with(configure);
    }

    /* ---- underline -------------------------------------------------------------- */

    /** A Underline value (underline(...)). */
    public static Underline underline(boolean on) {
        return Underline.with(on);
    }

    /** A Underline value (underline(...)). */
    public static Underline underline(Signal<Boolean> on) {
        return Underline.with(on);
    }

    /** A Underline value (underline(...)). */
    public static Underline underline() {
        return Underline.with();
    }

    /** A Underline value (underline(...)). */
    public static Underline underline(Consumer<Underline.Config> configure) {
        return Underline.with(configure);
    }

    /* ---- visible -------------------------------------------------------------- */

    /** A Visible value (visible(...)). */
    public static Visible visible(boolean visible) {
        return Visible.with(visible);
    }

    /** A Visible value (visible(...)). */
    public static Visible visible(Signal<Boolean> visible) {
        return Visible.with(visible);
    }

    /** A Visible value (visible(...)). */
    public static Visible visible(Consumer<Visible.Config> configure) {
        return Visible.with(configure);
    }

    /* ---- zIndex -------------------------------------------------------------- */

    /** A ZIndex value (zIndex(...)). */
    public static ZIndex zIndex(float value) {
        return ZIndex.with(value);
    }

    /** A ZIndex value (zIndex(...)). */
    public static ZIndex zIndex(Signal<Float> value) {
        return ZIndex.with(value);
    }

    /** A ZIndex value (zIndex(...)). */
    public static ZIndex zIndex(Consumer<ZIndex.Config> configure) {
        return ZIndex.with(configure);
    }

}