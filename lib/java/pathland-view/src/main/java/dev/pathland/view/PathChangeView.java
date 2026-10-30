package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;

import java.util.function.Consumer;

/**
 * A view wrapped by {@code onPathChange}: records the listener on the built node and
 * captures the active-path signal it should watch (the {@link Platform#ACTIVE_PATH}
 * environment value). The emitter registers a node-level effect that fires the
 * listeners when the signal changes, and destroys it when the subtree is replaced.
 */
final class PathChangeView implements View {

    private final View content;
    private final Consumer<String> listener;

    PathChangeView(View content, Consumer<String> listener) {
        this.content = content;
        this.listener = listener;
    }

    @Override
    public PathlandNode renderNode(Environment env) {
        PathlandNode node = content.renderNode(env);
        node.pathChangeListeners.add(listener);
        // The active-path signal provided by the host; null when not provided (inert).
        node.activePath = Environment.value(Platform.ACTIVE_PATH);
        return node;
    }
}