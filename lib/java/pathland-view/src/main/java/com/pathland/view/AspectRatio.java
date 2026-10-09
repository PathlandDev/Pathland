package com.pathland.view;

import java.util.function.Consumer;

/**
 * constrains the aspect ratio ({@code ASPECT_RATIO} + {@code CONTENT_MODE}).
 */
public final class AspectRatio implements ViewModifier {

    /** {@link AspectRatio} values. */
    public static final class Config {

        private Float ratio;
        private Float mode;

        /** Set the aspect ratio. */
        public Config ratio(float ratio) {
            this.ratio = ratio;
            return this;
        }

        /** Set the content mode. */
        public Config contentMode(ContentMode mode) {
            this.mode = (float) mode.wire();
            return this;
        }
    }

    private final float ratio;
    private final float mode;

    private AspectRatio(float ratio, float mode) {
        this.ratio = ratio;
        this.mode = mode;
    }

    public static AspectRatio of(float ratio, ContentMode mode) {
        return new AspectRatio(ratio, (float) mode.wire());
    }

    /** Configure the aspect ratio. */
    public static AspectRatio with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new AspectRatio(
                config.ratio != null ? config.ratio : 1f,
                config.mode != null ? config.mode : (float) ContentMode.FIT.wire());
    }

    @Override
    public View body(View content) {
        return Modified.props(content,
                Modified.prop(Properties.ASPECT_RATIO, ratio),
                Modified.prop(Properties.CONTENT_MODE, mode));
    }
}
