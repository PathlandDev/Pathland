package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.WritableSignal;

/**
 * A multi-line text editing area ({@code TextEditor}). Edits flow back as
 * {@code TEXT_CHANGED} and are written into the binding.
 */
public final class TextEditor implements View {

    private final WritableSignal<String> binding;

    private TextEditor(WritableSignal<String> binding) {
        this.binding = binding;
    }

    /** A multi-line text editing area bound to {@code binding}. */
    public static TextEditor of(WritableSignal<String> binding) {
        return new TextEditor(binding);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.TEXT_EDITOR);
        node.textBinding = binding;
        node.text = binding.get();
        node.textInput = binding::set;
        return node;
    }
}