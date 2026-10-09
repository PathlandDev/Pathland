package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * constrains the aspect ratio ({@code ASPECT_RATIO} + {@code CONTENT_MODE}).
 */
public final class AspectRatio implements ViewModifier {

    /** {@link AspectRatio} values. */
    public static final class Config {

        private Signal<Float> ratio;
        private Signal<ContentMode> mode;

        /** Set a static aspect ratio. */
        public Config ratio(float ratio) {
            this.ratio = Signals.constant(ratio);
            return this;
        }

        /** Bind the aspect ratio to a signal. */
        public Config ratio(Signal<Float> ratio) {
            this.ratio = ratio;
            return this;
        }

        /** Set a static content mode. */
        public Config contentMode(ContentMode mode) {
            this.mode = Signals.constant(mode);
            return this;
        }

        /** Bind the content mode to a signal. */
        public Config contentMode(Signal<ContentMode> mode) {
            this.mode = mode;
            return this;
        }
    }

    private final Signal<Float> ratio;
    private final Signal<ContentMode> mode;

    private AspectRatio(Config config) {
        this.ratio = config.ratio != null ? config.ratio : Signals.constant(1f);
        this.mode = config.mode != null ? config.mode : Signals.constant(ContentMode.FIT);
    }

    public static AspectRatio of(float ratio, ContentMode mode) {
        return new AspectRatio(new Config().ratio(ratio).contentMode(mode));
    }

    /** Configure the aspect ratio. */
    public static AspectRatio with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new AspectRatio(config);
    }

    @Override
    public View body(View content) {
        return Modified.props(content,
                Modified.prop(Properties.ASPECT_RATIO, ratio),
                Modified.prop(Properties.CONTENT_MODE, mode));
    }
}
