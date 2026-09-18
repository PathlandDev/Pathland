package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

/**
 * A view scoped to an environment value: the binding is active only for this subtree's
 * render, so values below inherit it without threading a parameter through every
 * {@link View#render} call.
 */
final class EnvironmentView implements View {

    private final View content;
    private final EnvironmentKey<Object> key;
    private final Object value;

    EnvironmentView(View content, EnvironmentKey<Object> key, Object value) {
        this.content = content;
        this.key = key;
        this.value = value;
    }

    @Override
    public PathlandNode render(Environment env) {
        EnvironmentValues scope = Environment.current().with(key, value);
        EnvironmentValues previous = Environment.current();
        Environment.within(scope);
        try {
            return content.render(env);
        } finally {
            Environment.restore(previous);
        }
    }
}