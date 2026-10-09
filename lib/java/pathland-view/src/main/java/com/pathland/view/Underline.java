package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * underlines the text ({@code UNDERLINE}).
 */
public final class Underline implements ViewModifier {

    /** {@link Underline} values. */
    public static final class Config {

        private Signal<Boolean> enabled;

        /** Set a static underline state. */
        public Config enabled(boolean enabled) {
            this.enabled = Signals.constant(enabled);
            return this;
        }

        /** Bind the underline state to a signal. */
        public Config enabled(Signal<Boolean> enabled) {
            this.enabled = enabled;
            return this;
        }
    }

    private final Config config;

    private Underline(Config config) {
        this.config = config;
    }

    /** Underline. */
    public static Underline of() {
        return new Underline(new Config().enabled(true));
    }

    /** Underline ({@code on} = underline). */
    public static Underline of(boolean on) {
        return new Underline(new Config().enabled(on));
    }

    /** Underline (enabled). */
    public static Underline with() {
        return new Underline(new Config().enabled(true));
    }

    /** Configure the underline. */
    public static Underline with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Underline(config);
    }

    @Override
    public View body(View content) {
        Signal<Boolean> enabled = config.enabled != null ? config.enabled : Signals.constant(true);
        return Modified.props(content, Modified.prop(Properties.UNDERLINE, enabled));
    }
}
