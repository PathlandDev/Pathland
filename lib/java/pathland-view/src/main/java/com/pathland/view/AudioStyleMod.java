package com.pathland.view;

/**
 * A {@link ViewModifier} that scopes an {@link AudioStyle} down the wrapped
 * subtree (SwiftUI {@code .audioStyle}). Applied via
 * {@code view.with(AudioStyleMod.of(style))}; the scope rides the generic
 * environment ({@link Environment#AUDIO_STYLE}).
 */
public final class AudioStyleMod implements ViewModifier {

    private final AudioStyle style;

    private AudioStyleMod(AudioStyle style) {
        this.style = style;
    }

    /** Scope {@code style} down the wrapped subtree. */
    public static AudioStyleMod of(AudioStyle style) {
        return new AudioStyleMod(style);
    }

    @Override
    public View body(View content) {
        return content.environment(Environment.AUDIO_STYLE, style);
    }
}