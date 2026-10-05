package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/** A scrollable content container ({@code ScrollView}). */
public final class ScrollView implements View {

    private final List<View> children;

    private ScrollView(View... children) {
        this.children = List.of(children);
    }

    /** A scrollable content container. */
    public static ScrollView of(View... children) {
        return new ScrollView(children);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.SCROLLVIEW);
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}