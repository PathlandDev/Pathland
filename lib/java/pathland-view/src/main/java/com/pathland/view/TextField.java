package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.WritableSignal;

/**
 * A single-line text input ({@code TextField}). The current value is bound to
 * a {@link WritableSignal<String>}; edits flow back through the host as
 * {@code TEXT_CHANGED} events and are written into the binding. The placeholder maps
 * to the {@code PROMPT} property.
 */
public final class TextField implements View {

    private final String placeholder;
    private final WritableSignal<String> binding;

    private TextField(String placeholder, WritableSignal<String> binding) {
        this.placeholder = placeholder;
        this.binding = binding;
    }

    /** A single-line text input bound to {@code binding}. */
    public static TextField of(String placeholder, WritableSignal<String> binding) {
        return new TextField(placeholder, binding);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.TEXT_FIELD);
        if (placeholder != null) {
            node.properties.put(Properties.PROMPT, placeholder);
        }
        node.textBinding = binding;
        node.text = binding.get();
        node.textInput = binding::set;
        return node;
    }
}