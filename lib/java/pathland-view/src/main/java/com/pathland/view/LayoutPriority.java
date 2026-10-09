package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * layout priority for stretching/shrinking.
 */
public final class LayoutPriority implements ViewModifier {

    /** {@link LayoutPriority} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static layout priority. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the layout priority to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private LayoutPriority(Config config) {
        this.config = config;
    }

    public static LayoutPriority of(float value) {
        return new LayoutPriority(new Config().value(value));
    }

    /** Configure the layout priority. */
    public static LayoutPriority with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new LayoutPriority(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(0f);
        return Modified.props(content, Modified.prop(Properties.LAYOUT_PRIORITY, value));
    }
}
