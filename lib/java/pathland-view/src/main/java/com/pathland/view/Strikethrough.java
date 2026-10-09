package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * strikethrough the text ({@code STRIKETHROUGH}).
 */
public final class Strikethrough implements ViewModifier {

    /** {@link Strikethrough} values. */
    public static final class Config {

        private Signal<Boolean> enabled;

        /** Set a static strikethrough state. */
        public Config enabled(boolean enabled) {
            this.enabled = Signals.constant(enabled);
            return this;
        }

        /** Bind the strikethrough state to a signal. */
        public Config enabled(Signal<Boolean> enabled) {
            this.enabled = enabled;
            return this;
        }
    }

    private final Config config;

    private Strikethrough(Config config) {
        this.config = config;
    }

    /** Strikethrough. */
    public static Strikethrough of() {
        return new Strikethrough(new Config().enabled(true));
    }

    /** Strikethrough ({@code on} = strikethrough). */
    public static Strikethrough of(boolean on) {
        return new Strikethrough(new Config().enabled(on));
    }

    /** Strikethrough (enabled). */
    public static Strikethrough with() {
        return new Strikethrough(new Config().enabled(true));
    }

    /** Configure the strikethrough. */
    public static Strikethrough with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Strikethrough(config);
    }

    @Override
    public View body(View content) {
        Signal<Boolean> enabled = config.enabled != null ? config.enabled : Signals.constant(true);
        return Modified.props(content, Modified.prop(Properties.STRIKETHROUGH, enabled));
    }
}
