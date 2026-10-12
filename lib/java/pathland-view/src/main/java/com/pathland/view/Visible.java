package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * shows/hides the view ({@code VISIBLE}).
 */
public final class Visible implements ViewModifier {

    /** {@link Visible} values. */
    public static final class Config {

        private Signal<Boolean> visible;

        /** Set a static visibility. */
        public Config visible(boolean visible) {
            this.visible = Signals.constant(visible);
            return this;
        }

        /** Bind visibility to a signal. */
        public Config visible(Signal<Boolean> visible) {
            this.visible = visible;
            return this;
        }
    }

    private final Config config;

    private Visible(Config config) {
        this.config = config;
    }

    /** Show ({@code visible} = shown). */
    static Visible with(boolean visible) {
        return Configurators.modifier(Config::new, Visible::new, c -> c.visible(visible));
    }

    /** Show ({@code visible} = shown), reactive. */
    static Visible with(Signal<Boolean> visible) {
        return Configurators.modifier(Config::new, Visible::new, c -> c.visible(visible));
    }

    /** Configure the visibility. */
    static Visible with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Visible(config);
    }

    @Override
    public View body(View content) {
        Signal<Boolean> visible = config.visible != null ? config.visible : Signals.constant(true);
        return Modified.props(content, Modified.prop(Properties.VISIBLE, visible));
    }
}
