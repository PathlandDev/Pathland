package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

/**
 * A multi-line text editing area ({@code TextEditor}). Edits flow back as
 * {@code TEXT_CHANGED} and are written into the binding.
 */
public final class TextEditor implements View, Configurable<TextEditor.Config> {

    /** {@link TextEditor} values. */
    public static final class Config implements View.Config {

        private WritableSignal<String> binding;

        /** Bind the text (two-way). */
        public Config text(WritableSignal<String> binding) {
            this.binding = binding;
            return this;
        }
    }

    private final Config config;

    private TextEditor(Config config) {
        this.config = config;
    }

    /** A multi-line text editing area bound to {@code binding}. */
    public static TextEditor of(WritableSignal<String> binding) {
        return new TextEditor(new Config().text(binding));
    }

    /** Configure the editor's values. */
    public static ViewBuilder<TextEditor, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new TextEditor(config));
    }

    /** Apply modifiers to a bare editor. */
    public static ViewBuilder<TextEditor, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new TextEditor(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.TEXT_EDITOR);
        if (config.binding != null) {
            node.textBinding = config.binding;
            node.text = config.binding.get();
            node.textInput = config.binding::set;
        }
        return node;
    }
}
