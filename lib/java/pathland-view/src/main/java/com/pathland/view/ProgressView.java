package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

/**
 * Determinate progress or an activity indicator ({@code ProgressView}).
 * {@code progress} is {@code 0..1}; {@link #indeterminate()} builds the animated
 * spinner variant ({@code IS_INDETERMINATE}).
 */
public final class ProgressView implements View {

    private final float progress;

    private ProgressView(float progress) {
        this.progress = progress;
    }

    /** A determinate progress bar ({@code value} in {@code 0..1}). */
    public static ProgressView of(float value) {
        return new ProgressView(value);
    }

    /** A determinate progress bar ({@code value} in {@code 0..1}). */
    public static ProgressView progress(float value) {
        return new ProgressView(value);
    }

    /** An indeterminate activity indicator (renderer-animated). */
    public static ProgressView indeterminate() {
        return new ProgressView(-1f);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.PROGRESS_VIEW);
        if (progress < 0f) {
            node.properties.put(Properties.IS_INDETERMINATE, 1);
        } else {
            node.properties.put(Properties.PROGRESS, progress);
        }
        return node;
    }
}