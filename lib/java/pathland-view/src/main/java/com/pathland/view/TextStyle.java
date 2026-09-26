package com.pathland.view;

/**
 * Predefined typographies for the {@code TEXT_STYLE} property (mirrors
 * {@code pathland_core::constants::text_style} and {@code spec/MODIFIERS.md}).
 * Each value selects a whole typography from the design system. The **heading**
 * styles ({@link #LARGE_TITLE} through {@link #HEADLINE}) imply a heading
 * element when applied to a {@link Text}: the renderer emits
 * {@code <h1>}–{@code <h5>} (the level comes from the style — no {@code ROLE}
 * needed). The non-heading styles render as plain {@code <span>} text. A raw
 * font modifier ({@link FontSize}, {@link FontWeightMod}, …) never implies a
 * heading — it only overrides the typography's visual.
 */
public enum TextStyle {

    /** The largest title style (heading level 1). */
    LARGE_TITLE(0),
    /** A primary title (heading level 2). */
    TITLE(1),
    /** A secondary title (heading level 3). */
    TITLE2(2),
    /** A tertiary title (heading level 4). */
    TITLE3(3),
    /** A headline (heading level 5). */
    HEADLINE(4),
    /** A sub-headline (NOT a heading — plain {@code <span>} text). */
    SUBHEADLINE(5),
    /** Body text (the default). */
    BODY(6),
    /** A callout (slightly larger than body). */
    CALLOUT(7),
    /** Footnote text. */
    FOOTNOTE(8),
    /** Caption text. */
    CAPTION(9),
    /** A second, smaller caption. */
    CAPTION2(10);

    private final int code;

    TextStyle(int code) {
        this.code = code;
    }

    /** The protocol enum code. */
    public int code() {
        return code;
    }
}