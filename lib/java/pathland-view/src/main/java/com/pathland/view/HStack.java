package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/**
 * A horizontal stack. {@code alignment} and {@code spacing} are constructor
 * (structural/layout) properties — never chainable modifiers.
 */
public final class HStack implements View {

    private final List<View> children;
    private final VerticalAlignment alignment;
    private final Float spacing;

    private HStack(View... children) {
        this(null, null, List.of(children));
    }

    private HStack(List<View> children) {
        this(null, null, children);
    }

    private HStack(VerticalAlignment alignment, float spacing, View... children) {
        this(alignment, spacing, List.of(children));
    }

    private HStack(VerticalAlignment alignment, Float spacing, List<View> children) {
        this.children = List.copyOf(children);
        this.alignment = alignment;
        this.spacing = spacing;
    }

    /** A horizontal stack. */
    public static HStack of(View... children) {
        return new HStack(children);
    }

    /** A horizontal stack. */
    public static HStack of(List<View> children) {
        return new HStack(children);
    }

    /** A horizontal stack with constructor layout properties. */
    public static HStack of(VerticalAlignment alignment, float spacing, View... children) {
        return new HStack(alignment, spacing, children);
    }

    /** A horizontal stack with constructor layout properties. */
    public static HStack of(VerticalAlignment alignment, Float spacing, List<View> children) {
        return new HStack(alignment, spacing, children);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.HSTACK);
        if (alignment != null) {
            node.properties.put(Properties.ALIGNMENT, (float) alignment.wire());
        }
        if (spacing != null) {
            node.properties.put(Properties.SPACING, spacing);
        }
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}