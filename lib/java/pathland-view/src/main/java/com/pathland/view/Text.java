package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * A text leaf. Content is a single {@link Signal<String>} — a bound text re-emits
 * only this node's {@code SET_TEXT} when the signal changes. Static text is sugar
 * for a constant signal. Values are configured with {@code Text.with(t -> t.text(…))}.
 */
public final class Text implements View, Configurable<Text.Config> {

    /** {@link Text} values. */
    public static final class Config implements View.Config {

        private Signal<String> binding = Signals.constant("");

        /** Set the (static) text. */
        public Config text(String text) {
            this.binding = Signals.constant(text);
            return this;
        }

        /** Bind the text to a signal (reactive). */
        public Config text(Signal<String> binding) {
            this.binding = binding;
            return this;
        }

        Signal<String> binding() {
            return binding;
        }
    }

    private final Config config;

    private Text(Config config) {
        this.config = config;
    }

    /** Static text. */
    public static Text of(String text) {
        return new Text(new Config().text(text));
    }

    /** Reactive text (the current value is read at render time). */
    public static Text of(Signal<String> binding) {
        return new Text(new Config().text(binding));
    }

    /** Configure the text's value. */
    public static ViewBuilder<Text, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Text(config));
    }

    /** Apply modifiers to an empty text. */
    public static ViewBuilder<Text, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Text(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.TEXT);
        node.textBinding = config.binding();
        node.text = config.binding().get();
        return node;
    }
}
