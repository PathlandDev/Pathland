package com.pathland.view;

/**
 * The presentation of a {@link Label} (SwiftUI {@code LabelStyle}: the three
 * canonical modes). A {@code LabelStyle} decides which of the label's two parts —
 * title, icon — are rendered (spec DSL.md §5.5).
 *
 * <p>DSL-only control flow: the style picks which children the label emits; it is
 * <em>never</em> a wire property (the emitted child tree <em>is</em> the wire
 * surface). Scoped down a subtree with {@code LabelStyleMod} via the generic
 * environment ({@link Environment#LABEL_STYLE}), nearest-wins.
 */
public enum LabelStyle {

    /** Both the title and the icon are shown (the default). */
    TITLE_AND_ICON(true, true),
    /** Only the title is shown; the icon is omitted. */
    TITLE_ONLY(true, false),
    /** Only the icon is shown; the title still drives the accessibility label. */
    ICON_ONLY(false, true);

    private final boolean showTitle;
    private final boolean showIcon;

    LabelStyle(boolean showTitle, boolean showIcon) {
        this.showTitle = showTitle;
        this.showIcon = showIcon;
    }

    /** Whether this style renders the title. */
    public boolean showsTitle() {
        return showTitle;
    }

    /** Whether this style renders the icon. */
    public boolean showsIcon() {
        return showIcon;
    }
}