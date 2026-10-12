package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

/** A view wrapped by {@code pointerEvents(mask)}: ORs the listener bits into {@code EVENT_LISTENERS}. */
final class PointerEventsView implements View {

    private final View content;
    private final int mask;

    PointerEventsView(View content, int mask) {
        this.content = content;
        this.mask = mask;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = content.render(env);
        Object existing = node.properties.get(Properties.EVENT_LISTENERS);
        int current = existing instanceof Integer i ? i : 0;
        node.properties.put(Properties.EVENT_LISTENERS, current | mask);
        return node;
    }
}