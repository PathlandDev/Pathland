package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/** A scrollable content container ({@code ScrollView}). */
public final class ScrollView implements View, Configurable<ScrollView.Config>, ChildrenView {

    /** {@link ScrollView} has no values (the axes are renderer-owned defaults). */
    public static final class Config implements View.Config {
    }

    private final Config config = new Config();
    private List<View> children = List.of();

    private ScrollView(List<View> children) {
        this.children = children;
    }

    /** A scrollable content container. */
    public static ScrollView of(View... children) {
        return new ScrollView(List.of(children));
    }

    /** Supply the scrollable content. */
    public static ViewBuilder<ScrollView, Config> children(View... children) {
        return new ViewBuilder<>(new ScrollView(List.of())).children(children);
    }

    /** Apply modifiers to an empty scroll view. */
    public static ViewBuilder<ScrollView, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new ScrollView(List.of())).modifiers(modifiers);
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
        PathlandNode node = new PathlandNode(Components.SCROLLVIEW);
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}
