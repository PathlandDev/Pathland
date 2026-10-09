package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

/**
 * A single-line text input ({@code TextField}). The current value is bound to
 * a {@link WritableSignal<String>}; edits flow back through the host as
 * {@code TEXT_CHANGED} events and are written into the binding. The placeholder maps
 * to the {@code PROMPT} property.
 */
public final class TextField implements View, Configurable<TextField.Config> {

    /** {@link TextField} values. */
    public static final class Config implements View.Config {

        private Signal<String> placeholder;
        private WritableSignal<String> binding;

        /** Set a static placeholder ({@code PROMPT}). */
        public Config placeholder(String placeholder) {
            this.placeholder = Signals.constant(placeholder);
            return this;
        }

        /** Bind the placeholder ({@code PROMPT}) to a signal. */
        public Config placeholder(Signal<String> placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        /** Bind the current value (two-way). */
        public Config text(WritableSignal<String> binding) {
            this.binding = binding;
            return this;
        }
    }

    private final Config config;

    private TextField(Config config) {
        this.config = config;
    }

    /** A single-line text input bound to {@code binding}. */
    public static TextField of(String placeholder, WritableSignal<String> binding) {
        return new TextField(new Config().placeholder(placeholder).text(binding));
    }

    /** Configure the text field's values. */
    public static ViewBuilder<TextField, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new TextField(config));
    }

    /** Apply modifiers to a bare text field. */
    public static ViewBuilder<TextField, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new TextField(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.TEXT_FIELD);
        if (config.placeholder != null) {
            node.property(Properties.PROMPT, config.placeholder);
        }
        if (config.binding != null) {
            node.textBinding = config.binding;
            node.text = config.binding.get();
            node.textInput = config.binding::set;
        }
        return node;
    }
}
