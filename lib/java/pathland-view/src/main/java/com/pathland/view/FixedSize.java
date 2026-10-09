package com.pathland.view;

import java.util.function.Consumer;

/**
 * fixes the view to its intrinsic content size ({@code FIXED_SIZE_*}).
 */
public final class FixedSize implements ViewModifier {

    /** {@link FixedSize} values. */
    public static final class Config {

        private Boolean horizontal;
        private Boolean vertical;

        /** Fix the horizontal axis. */
        public Config horizontal(boolean horizontal) {
            this.horizontal = horizontal;
            return this;
        }

        /** Fix the vertical axis. */
        public Config vertical(boolean vertical) {
            this.vertical = vertical;
            return this;
        }
    }

    private final boolean horizontal;
    private final boolean vertical;

    private FixedSize(boolean horizontal, boolean vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    /** Fix both axes. */
    public static FixedSize of() {
        return new FixedSize(true, true);
    }

    /** Fix per axis. */
    public static FixedSize of(boolean horizontal, boolean vertical) {
        return new FixedSize(horizontal, vertical);
    }

    /** Fix both axes. */
    public static FixedSize with() {
        return new FixedSize(true, true);
    }

    /** Configure the fixed axes. */
    public static FixedSize with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new FixedSize(
                config.horizontal == null || config.horizontal,
                config.vertical == null || config.vertical);
    }

    @Override
    public View body(View content) {
        return Modified.props(content,
                Modified.prop(Properties.FIXED_SIZE_HORIZONTAL, horizontal ? 1 : 0),
                Modified.prop(Properties.FIXED_SIZE_VERTICAL, vertical ? 1 : 0));
    }
}
