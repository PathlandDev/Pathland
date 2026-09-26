package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

/**
 * A text leaf. Content is a single {@link Signal<String>} — a bound text
 * re-emits only this node's {@code SET_TEXT} when the signal changes. Static
 * text is sugar for a constant signal ({@code Text.of("…")} ≡
 * {@code Text.of(Signals.constant("…"))}; the emitter treats constants as plain
 * properties — no binding overhead).
 */
public final class Text implements View {

    private final Signal<String> binding;

    private Text(Signal<String> binding) {
        this.binding = binding;
    }

    /** Static text. */
    public static Text of(String text) {
        return new Text(Signals.constant(text));
    }

    /** Reactive text (the current value is read at render time). */
    public static Text of(Signal<String> binding) {
        return new Text(binding);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.TEXT);
        node.textBinding = binding;
        node.text = binding.get();
        return node;
    }
}