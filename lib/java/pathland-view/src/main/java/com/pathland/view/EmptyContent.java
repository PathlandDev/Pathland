package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

/**
 * A sentinel style content meaning "no content" ({@code EmptyView}). A style
 * returns it when the control should render its native shell without custom content —
 * e.g. {@link NativeAudioStyle} (renderer-native media controls). A control recognizes
 * the sentinel and attaches no child, so it stays in Native Token Mode
 * (spec PRIMITIVES.md §2, spec DSL.md §5.7).
 *
 * <p>It is never rendered through {@link #render(Environment)} by a well-behaved
 * control; the fallback node exists only to keep the {@link View} contract total.
 */
public enum EmptyContent implements View {

    INSTANCE;

    @Override
    public PathlandNode render(Environment env) {
        return new PathlandNode(Components.VSTACK);
    }
}
