package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.WritableSignal;

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

    /** Configure the editor's values. */
/** An empty TextEditor (no values). */
    public static ViewBuilder<TextEditor, Config> make() {
        return new ViewBuilder<>(new TextEditor(new Config()));
    }

    public static ViewBuilder<TextEditor, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new TextEditor(config));
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode renderNode(Environment env) {
        PathlandNode node = new PathlandNode(Components.TEXT_EDITOR);
        if (config.binding != null) {
            node.textBinding = config.binding;
            node.text = dev.pathland.view.signal.Signals.untracked(config.binding::get);
            node.textInput = config.binding::set;
        }
        return node;
    }
}
