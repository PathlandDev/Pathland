package com.pathland.view;

/**
 * A font specification (SwiftUI {@code Font}): a predefined typography from the
 * design system, a custom font family + size, or a system font with an exact
 * size/weight/design. Applied with {@link FontMod} / {@link View#font(Font)}.
 *
 * <p>A heading typography ({@link TextStyle} {@code LargeTitle}…{@code Headline})
 * makes a {@link Text} a heading element ({@code <h1>}–{@code <h5>}); a custom
 * or system font never implies a heading — it only styles the text. The
 * individual modifiers ({@link FontSize}, {@link FontWeightMod},
 * {@link FontFamily}, {@link FontDesignMod}, …) remain available for one-off
 * overrides on top.
 */
public final class Font {

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

    /** A custom font family + size (SwiftUI {@code .font(.custom("…", size:))}). */
    public static Font custom(String family, float size) {
        return new Font(null, family, size, null, null);
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
     *  (SwiftUI {@code .font(.system(size:weight:design:))}). */
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
}