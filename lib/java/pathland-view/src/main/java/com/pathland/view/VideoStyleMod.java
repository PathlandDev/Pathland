package com.pathland.view;

/**
 * A {@link ViewModifier} that scopes a {@link VideoStyle} down the wrapped
 * subtree ({@code .videoStyle}). The scope rides the generic environment
 * ({@link Environment#VIDEO_STYLE}).
 */
public final class VideoStyleMod implements ViewModifier {

    private final VideoStyle style;

    private VideoStyleMod(VideoStyle style) {
        this.style = style;
    }

    /** Scope {@code style} down the wrapped subtree. */
    public static VideoStyleMod of(VideoStyle style) {
        return new VideoStyleMod(style);
    }

    @Override
    public View body(View content) {
        return content.environment(Environment.VIDEO_STYLE, style);
    }
}