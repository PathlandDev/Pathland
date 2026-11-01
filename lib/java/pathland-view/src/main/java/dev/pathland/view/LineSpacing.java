package dev.pathland.view;

import dev.pathland.view.core.Configurators;
import dev.pathland.view.core.Modified;
import dev.pathland.view.core.protocol.Properties;
import dev.pathland.view.core.View;
import dev.pathland.view.core.ViewModifier;

import dev.pathland.view.core.signal.Signal;
import dev.pathland.view.core.signal.Signals;

import java.util.function.Consumer;

/**
 * line spacing in points.
 */
public final class LineSpacing implements ViewModifier {

    /** {@link LineSpacing} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static line spacing. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the line spacing to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private LineSpacing(Config config) {
        this.config = config;
    }

    static LineSpacing with(float value) {
        return Configurators.modifier(Config::new, LineSpacing::new, c -> c.value(value));
    }

    static LineSpacing with(Signal<Float> value) {
        return Configurators.modifier(Config::new, LineSpacing::new, c -> c.value(value));
    }

    /** Configure the line spacing. */
    static LineSpacing with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new LineSpacing(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(0f);
        return Modified.props(content, Modified.prop(Properties.LINE_SPACING, value));
    }
}
