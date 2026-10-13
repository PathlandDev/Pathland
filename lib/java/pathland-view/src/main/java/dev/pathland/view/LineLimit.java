package dev.pathland.view;

import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * limits the number of rendered lines ({@code 0} = unlimited).
 */
public final class LineLimit implements ViewModifier {

    /** {@link LineLimit} values. */
    public static final class Config {

        private Signal<Integer> value;

        /** Set a static line limit ({@code 0} = unlimited). */
        public Config value(int value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the line limit to a signal. */
        public Config value(Signal<Integer> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private LineLimit(Config config) {
        this.config = config;
    }

    /** Line limit ({@code 0} = unlimited). */
    static LineLimit with(int lines) {
        return Configurators.modifier(Config::new, LineLimit::new, c -> c.value(lines));
    }

    /** Line limit bound to a reactive signal ({@code 0} = unlimited). */
    static LineLimit with(Signal<Integer> value) {
        return Configurators.modifier(Config::new, LineLimit::new, c -> c.value(value));
    }

    /** Configure the line limit. */
    static LineLimit with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new LineLimit(config);
    }

    @Override
    public View body(View content) {
        Signal<Integer> value = config.value != null ? config.value : Signals.constant(0);
        return Modified.props(content, Modified.prop(Properties.LINE_LIMIT, value));
    }
}
