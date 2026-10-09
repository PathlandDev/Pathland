package com.pathland.view;

/**
 * The presentation of a {@link Label} ({@code LabelStyle}). A style turns a
 * label's {@link Configuration} (its title and icon parts) into the label's content
 * view — it decides which parts render and how they are arranged.
 *
 * <p>DSL-only control flow: the style shapes the emitted child tree, which <em>is</em>
 * the wire surface; there is never a style property. Scoped down a subtree with
 * {@link LabelStyleMod} via the generic environment ({@link Environment#LABEL_STYLE}),
 * nearest-wins (spec DSL.md §5.7). Built-ins: {@link DefaultLabelStyle} (title + icon),
 * {@link TitleOnlyLabelStyle}, {@link IconOnlyLabelStyle}.
 */
public interface LabelStyle extends Style {

    /** Build the styled label content for {@code config}. */
    View makeBody(Configuration config);

    /** Scopes this style down the wrapped subtree ({@code .modifiers(LabelStyle)}). */
    @Override
    default View body(View content) {
        return content.environment(Environment.LABEL_STYLE, this);
    }

    /**
     * The parts of the label being styled. A part is {@code null} when it is absent
     * (no title text / no icon) — the built-in styles omit an absent part, and a custom
     * style should check {@link #hasTitle()}/{@link #hasIcon()} before styling it.
     *
     * <p>Delta: the {@code LabelStyleConfiguration.title}/{@code .icon}
     * are always-present views (empty when the part is absent); Pathland passes
     * {@code null} instead, so an absent part is easy to omit.
     */
    record Configuration(View title, View icon) {

        /** Whether the label has a title part. */
        public boolean hasTitle() {
            return title != null;
        }

        /** Whether the label has an icon part. */
        public boolean hasIcon() {
            return icon != null;
        }
    }
}
