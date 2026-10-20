package dev.pathland.view;

import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;

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

    /** Underline ({@code on} = underline). */
    static Underline with(boolean on) {
        return Configurators.modifier(Config::new, Underline::new, c -> c.enabled(on));
    }

    /** Underline ({@code on} = underline), reactive. */
    static Underline with(Signal<Boolean> on) {
        return Configurators.modifier(Config::new, Underline::new, c -> c.enabled(on));
    }

    /** Underline (enabled). */
    static Underline with() {
        return new Underline(new Config().enabled(true));
    }

    /** Configure the underline. */
    static Underline with(Consumer<Config> configure) {
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
