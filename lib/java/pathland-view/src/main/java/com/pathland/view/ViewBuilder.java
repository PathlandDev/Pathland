package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The chainable result of a view's static {@code with(...)} / {@code modifiers(...)}
 * / {@code children(...)} entry points (spec DSL.md §2). The three operations may
 * be used in **any order**; a later call overrides an earlier value for the same
 * property.
 *
 * <p>Java cannot declare a static and an instance method with the same signature,
 * so the view classes hold the three operations **statically** and the chainable
 * forms live here, on a distinct builder type implementing {@link View}.
 *
 * @param <V> the concrete view
 * @param <C> its value config
 */
public final class ViewBuilder<V extends View & Configurable<C>, C extends View.Config>
        implements View {

    private final V view;
    private final List<ViewModifier> modifiers = new ArrayList<>();
    private final List<View> children = new ArrayList<>();

    public ViewBuilder(V view) {
        this.view = view;
    }

    /** Configure the view's values. */
    public ViewBuilder<V, C> with(Consumer<C> configure) {
        configure.accept(view.config());
        return this;
    }

    /** Apply modifiers (innermost-first). */
    public ViewBuilder<V, C> modifiers(ViewModifier... modifiers) {
        for (ViewModifier modifier : modifiers) {
            this.modifiers.add(modifier);
        }
        return this;
    }

    /** Supply the content (content-bearing views only). */
    public ViewBuilder<V, C> children(View... children) {
        for (View child : children) {
            this.children.add(child);
        }
        return this;
    }

    @Override
    public PathlandNode render(Environment env) {
        if (!children.isEmpty() && view instanceof ChildrenView content) {
            content.setChildren(children);
        }
        View result = view;
        for (ViewModifier modifier : modifiers) {
            result = Modified.apply(result, modifier);
        }
        return result.render(env);
    }
}
