package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/**
 * A transparent container that composites its children ({@code Group}). Today
 * it materializes as a {@code VSTACK} container node with no spacing/alignment — the
 * grouping itself carries no layout. A future protocol component may let it splice
 * children directly into the parent.
 */
public final class Group implements View, Configurable<Group.Config>, ChildrenView {

    /** {@link Group} has no values. */
    public static final class Config implements View.Config {
    }

    private final Config config = new Config();
    private List<View> children = List.of();

    private Group(List<View> children) {
        this.children = children;
    }

    /** A group of child views. */
    public static Group of(View... children) {
        return new Group(List.of(children));
    }

    /** Supply the group's children. */
    public static ViewBuilder<Group, Config> children(View... children) {
        return new ViewBuilder<>(new Group(List.of())).children(children);
    }

    /** Apply modifiers to an empty group. */
    public static ViewBuilder<Group, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Group(List.of())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public void setChildren(List<View> children) {
        this.children = List.copyOf(children);
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
