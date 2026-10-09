package com.pathland.view;

import java.util.ArrayList;
import java.util.List;

/**
 * A font specification ({@code Font}): a predefined typography from the
 * design system, a custom font family + size, or a system font with an exact
 * size/weight/design. The {@code Font} value **is** the modifier
 * ({@code .modifiers(Font.headline())}).
 *
 * <p>A heading typography ({@link TextStyle} {@code LargeTitle}…{@code Headline})
 * makes a {@link Text} a heading element ({@code <h1>}–{@code <h5>}); a custom
 * or system font never implies a heading — it only styles the text. The
 * individual modifiers ({@link FontSize}, {@link FontWeight},
 * {@link FontFamily}, {@link FontDesign}, …) remain available for one-off
 * overrides on top.
 */
public final class Font implements ViewModifier {

    private final TextStyle style; // non-null → predefined typography (TEXT_STYLE)
    private final String family;   // custom family (FONT_FAMILY)
    private final Float size;      // custom/system size (FONT_SIZE)
    private final FontWeight weight; // system weight (FONT_WEIGHT)
    private final FontDesign design; // system design (FONT_DESIGN)

    private Font(TextStyle style, String family, Float size, FontWeight weight, FontDesign design) {
        this.style = style;
        this.family = family;
        this.size = size;
        this.weight = weight;
        this.design = design;
    }

    // ── Predefined typographies (the design-system enum → TEXT_STYLE) ──────

    public static Font largeTitle() { return new Font(TextStyle.LARGE_TITLE, null, null, null, null); }
    public static Font title() { return new Font(TextStyle.TITLE, null, null, null, null); }
    public static Font title2() { return new Font(TextStyle.TITLE2, null, null, null, null); }
    public static Font title3() { return new Font(TextStyle.TITLE3, null, null, null, null); }
    public static Font headline() { return new Font(TextStyle.HEADLINE, null, null, null, null); }
    public static Font subheadline() { return new Font(TextStyle.SUBHEADLINE, null, null, null, null); }
    public static Font body() { return new Font(TextStyle.BODY, null, null, null, null); }
    public static Font callout() { return new Font(TextStyle.CALLOUT, null, null, null, null); }
    public static Font footnote() { return new Font(TextStyle.FOOTNOTE, null, null, null, null); }
    public static Font caption() { return new Font(TextStyle.CAPTION, null, null, null, null); }
    public static Font caption2() { return new Font(TextStyle.CAPTION2, null, null, null, null); }

    /** A custom font family + size ({@code .font(.custom("…", size:))}). */
    public static Font custom(String family, float size) {
        return new Font(null, family, size, null, null);
    }

    /** A fully-custom typography: family + size + weight + design. */
    public static Font custom(String family, float size, FontWeight weight, FontDesign design) {
        return new Font(null, family, size, weight, design);
    }

    /** A system font with an exact size. */
    public static Font system(float size) {
        return new Font(null, null, size, null, null);
    }

    /** A system font with an exact size + weight. */
    public static Font system(float size, FontWeight weight) {
        return new Font(null, null, size, weight, null);
    }

    /** A system font with an exact size + weight + design
     *  ({@code .font(.system(size:weight:design:))}). */
    public static Font system(float size, FontWeight weight, FontDesign design) {
        return new Font(null, null, size, weight, design);
    }

    TextStyle style() {
        return style;
    }

    String family() {
        return family;
    }

    Float size() {
        return size;
    }

    FontWeight weight() {
        return weight;
    }

    FontDesign design() {
        return design;
    }

    /** The font is itself the modifier. */
    @Override
    public View body(View content) {
        if (style != null) {
            // Predefined typography: the heading styles imply a heading element.
            return Modified.props(content, Modified.prop(Properties.TEXT_STYLE, (float) style.code()));
        }
        List<Modified.Prop> props = new ArrayList<>(4);
        if (family != null) {
            props.add(Modified.prop(Properties.FONT_FAMILY, family));
        }
        if (size != null) {
            props.add(Modified.prop(Properties.FONT_SIZE, size));
        }
        if (weight != null) {
            props.add(Modified.prop(Properties.FONT_WEIGHT, (float) weight.wire()));
        }
        if (design != null) {
            props.add(Modified.prop(Properties.FONT_DESIGN, (float) design.wire()));
        }
        return Modified.props(content, props.toArray(new Modified.Prop[0]));
    }
}