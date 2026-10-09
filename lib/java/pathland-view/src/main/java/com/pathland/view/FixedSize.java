package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * fixes the view to its intrinsic content size ({@code FIXED_SIZE_*}).
 */
public final class FixedSize implements ViewModifier {

    /** {@link FixedSize} values. */
    public static final class Config {

        private Signal<Boolean> horizontal;
        private Signal<Boolean> vertical;

        /** Fix the horizontal axis. */
        public Config horizontal(boolean horizontal) {
            this.horizontal = Signals.constant(horizontal);
            return this;
        }

        /** Bind the horizontal axis to a signal. */
        public Config horizontal(Signal<Boolean> horizontal) {
            this.horizontal = horizontal;
            return this;
        }

        /** Fix the vertical axis. */
        public Config vertical(boolean vertical) {
            this.vertical = Signals.constant(vertical);
            return this;
        }

        /** Bind the vertical axis to a signal. */
        public Config vertical(Signal<Boolean> vertical) {
            this.vertical = vertical;
            return this;
        }
    }

    private final Signal<Boolean> horizontal;
    private final Signal<Boolean> vertical;

    private FixedSize(Config config) {
        this.horizontal = config.horizontal != null ? config.horizontal : Signals.constant(true);
        this.vertical = config.vertical != null ? config.vertical : Signals.constant(true);
    }

    /** Fix both axes. */
    public static FixedSize of() {
        return new FixedSize(new Config());
    }

    /** Fix per axis. */
    public static FixedSize of(boolean horizontal, boolean vertical) {
        return new FixedSize(new Config().horizontal(horizontal).vertical(vertical));
    }

    /** Fix both axes. */
    public static FixedSize with() {
        return new FixedSize(new Config());
    }

    /** Configure the fixed axes. */
    public static FixedSize with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new FixedSize(config);
    }

    @Override
    public View body(View content) {
        return Modified.props(content,
                Modified.prop(Properties.FIXED_SIZE_HORIZONTAL, horizontal),
                Modified.prop(Properties.FIXED_SIZE_VERTICAL, vertical));
    }
}
