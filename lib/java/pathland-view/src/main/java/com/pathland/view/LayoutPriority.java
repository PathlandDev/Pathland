package com.pathland.view;

import java.util.function.Consumer;

/**
 * layout priority for stretching/shrinking.
 */
public final class LayoutPriority implements ViewModifier {

    /** {@link LayoutPriority} values. */
    public static final class Config {

        private float value;

        /** Set the layout priority. */
        public Config value(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private LayoutPriority(float value) {
        this.value = value;
    }

    public static LayoutPriority of(float value) {
        return new LayoutPriority(value);
    }

    /** Configure the layout priority. */
    public static LayoutPriority with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new LayoutPriority(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.LAYOUT_PRIORITY, value));
    }
}
