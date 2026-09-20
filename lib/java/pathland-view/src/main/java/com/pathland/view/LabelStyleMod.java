package com.pathland.view;

/**
 * A {@link ViewModifier} that scopes a {@link LabelStyle} down the wrapped subtree
 * (SwiftUI {@code .labelStyle}). Applied via {@code view.modifier(LabelStyleMod.of(style))};
 * the scope rides the generic environment ({@link Environment#LABEL_STYLE}).
 */
public final class LabelStyleMod implements ViewModifier {

    private final LabelStyle style;

    private LabelStyleMod(LabelStyle style) {
        this.style = style;
    }

    /** Scope {@code style} down the wrapped subtree. */
    public static LabelStyleMod of(LabelStyle style) {
        return new LabelStyleMod(style);
    }

    @Override
    public View body(View content) {
        return content.environment(Environment.LABEL_STYLE, style);
    }
}