package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/**
 * An overlapping stack (children rendered on top of each other). Rendered as a plain
 * container; the native renderer decides the exact stacking.
 */
public final class ZStack implements View {

    private final List<View> children;
    private final Alignment alignment;

    private ZStack(View... children) {
        this(null, List.of(children));
    }

    private ZStack(List<View> children) {
        this(null, children);
    }

    private ZStack(Alignment alignment, View... children) {
        this(alignment, List.of(children));
    }

    private ZStack(Alignment alignment, List<View> children) {
        this.children = List.copyOf(children);
        this.alignment = alignment;
    }

    /** An overlapping stack. */
    public static ZStack of(View... children) {
        return new ZStack(children);
    }

    /** An overlapping stack. */
    public static ZStack of(List<View> children) {
        return new ZStack(children);
    }

    /** An overlapping stack with a 2D alignment (positions children on both axes). */
    public static ZStack of(Alignment alignment, View... children) {
        return new ZStack(alignment, children);
    }

    /** An overlapping stack with a 2D alignment (positions children on both axes). */
    public static ZStack of(Alignment alignment, List<View> children) {
        return new ZStack(alignment, children);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.ZSTACK);
        if (alignment != null) {
            node.properties.put(Properties.ALIGNMENT, (float) alignment.wire());
        }
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}