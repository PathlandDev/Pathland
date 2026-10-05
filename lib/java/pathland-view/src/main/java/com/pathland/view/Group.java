package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/**
 * A transparent container that composites its children ({@code Group}). Today
 * it materializes as a {@code VSTACK} container node with no spacing/alignment — the
 * grouping itself carries no layout. A future protocol component may let it splice
 * children directly into the parent.
 */
public final class Group implements View {

    private final List<View> children;

    private Group(View... children) {
        this.children = List.of(children);
    }

    /** A group of child views. */
    public static Group of(View... children) {
        return new Group(children);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.VSTACK);
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}