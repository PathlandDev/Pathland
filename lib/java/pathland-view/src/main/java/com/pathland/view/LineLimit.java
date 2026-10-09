package com.pathland.view;

import java.util.function.Consumer;

/**
 * limits the number of rendered lines ({@code 0} = unlimited).
 */
public final class LineLimit implements ViewModifier {

    /** {@link LineLimit} values. */
    public static final class Config {

        private int value;

        /** Set the line limit ({@code 0} = unlimited). */
        public Config value(int value) {
            this.value = value;
            return this;
        }
    }

    private final int lines;

    private LineLimit(int lines) {
        this.lines = lines;
    }

    /** Line limit ({@code 0} = unlimited). */
    public static LineLimit of(int lines) {
        return new LineLimit(lines);
    }

    /** Configure the line limit. */
    public static LineLimit with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new LineLimit(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.LINE_LIMIT, lines));
    }
}
