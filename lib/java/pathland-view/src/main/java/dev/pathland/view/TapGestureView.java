package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;

/**
 * A view wrapped by {@code onTapGesture}: declares the pointer down/up event listeners
 * and records the app-side action on the built node so the host can route a recognized
 * tap back to it.
 */
final class TapGestureView implements View {

    private final View content;
    private final Runnable action;

    TapGestureView(View content, Runnable action) {
        this.content = content;
        this.action = action;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = content.render(env);
        Object existing = node.properties.get(Properties.EVENT_LISTENERS);
        int mask = existing instanceof Integer i ? i : 0;
        node.properties.put(
                Properties.EVENT_LISTENERS,
                mask | Commands.Listeners.POINTER_DOWN | Commands.Listeners.POINTER_UP);
        node.tapActions.add(action);
        return node;
    }
}