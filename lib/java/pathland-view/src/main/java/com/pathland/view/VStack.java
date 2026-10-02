package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/**
 * A vertical stack. {@code alignment} and {@code spacing} are constructor
 * (structural/layout) properties — never chainable modifiers.
 */
public final class VStack implements View {

    private final List<View> children;
    private final HorizontalAlignment alignment;
    private final Float spacing;

    private VStack(View... children) {
        this(null, null, List.of(children));
    }

    private VStack(List<View> children) {
        this(null, null, children);
    }

    private VStack(HorizontalAlignment alignment, float spacing, View... children) {
        this(alignment, spacing, List.of(children));
    }

    private VStack(HorizontalAlignment alignment, Float spacing, List<View> children) {
        this.children = List.copyOf(children);
        this.alignment = alignment;
        this.spacing = spacing;
    }

    /** A vertical stack. */
    public static VStack of(View... children) {
        return new VStack(children);
    }

    /** A vertical stack. */
    public static VStack of(List<View> children) {
        return new VStack(children);
    }

    /** A vertical stack with constructor layout properties. */
    public static VStack of(HorizontalAlignment alignment, float spacing, View... children) {
        return new VStack(alignment, spacing, children);
    }

    /** A vertical stack with constructor layout properties. */
    public static VStack of(HorizontalAlignment alignment, Float spacing, List<View> children) {
        return new VStack(alignment, spacing, children);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.VSTACK);
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