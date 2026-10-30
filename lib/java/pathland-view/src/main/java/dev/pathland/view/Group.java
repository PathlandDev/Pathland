package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;

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

    /** An empty group. */
    public static ViewBuilder<Group, Config> make() {
        return new ViewBuilder<>(new Group(List.of()));
    }

    /** Supply the group's children. */
    public static ViewBuilder<Group, Config> children(View... children) {
        return new ViewBuilder<>(new Group(List.of())).children(children);
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
    public PathlandNode renderNode(Environment env) {
        PathlandNode node = new PathlandNode(Components.VSTACK);
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}
