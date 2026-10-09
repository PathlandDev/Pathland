package com.pathland.view;

import java.util.function.Consumer;

/**
 * clips the view to a shape ({@code CLIPS_TO_BOUNDS} + {@code SHAPE_KIND}).
 */
public final class ClipShape implements ViewModifier {

    /** {@link ClipShape} values. */
    public static final class Config {

        private ShapeKind shape;

        /** Set the clip shape. */
        public Config shape(ShapeKind shape) {
            this.shape = shape;
            return this;
        }
    }

    private final Config config;

    private ClipShape(Config config) {
        this.config = config;
    }

    public static ClipShape of(ShapeKind kind) {
        return new ClipShape(new Config().shape(kind));
    }

    /** Configure the clip shape. */
    public static ClipShape with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ClipShape(config);
    }

    @Override
    public View body(View content) {
        return Modified.props(content,
                Modified.prop(Properties.CLIPS_TO_BOUNDS, 1),
                Modified.prop(Properties.SHAPE_KIND,
                        (float) (config.shape != null ? config.shape : ShapeKind.RECTANGLE).wire()));
    }
}
