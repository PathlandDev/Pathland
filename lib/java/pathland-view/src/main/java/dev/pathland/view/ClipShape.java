package dev.pathland.view;

import dev.pathland.view.core.Configurators;
import dev.pathland.view.core.Modified;
import dev.pathland.view.core.protocol.Properties;
import dev.pathland.view.core.ShapeKind;
import dev.pathland.view.core.View;
import dev.pathland.view.core.ViewModifier;

import dev.pathland.view.core.signal.Signal;
import dev.pathland.view.core.signal.Signals;

import java.util.function.Consumer;

/**
 * clips the view to a shape ({@code CLIPS_TO_BOUNDS} + {@code SHAPE_KIND}).
 */
public final class ClipShape implements ViewModifier {

    /** {@link ClipShape} values. */
    public static final class Config {

        private Signal<ShapeKind> shape;

        /** Set a static clip shape. */
        public Config shape(ShapeKind shape) {
            this.shape = Signals.constant(shape);
            return this;
        }

        /** Bind the clip shape to a signal. */
        public Config shape(Signal<ShapeKind> shape) {
            this.shape = shape;
            return this;
        }
    }

    private final Config config;

    private ClipShape(Config config) {
        this.config = config;
    }

    static ClipShape with(ShapeKind kind) {
        return Configurators.modifier(Config::new, ClipShape::new, c -> c.shape(kind));
    }

    static ClipShape with(Signal<ShapeKind> kind) {
        return Configurators.modifier(Config::new, ClipShape::new, c -> c.shape(kind));
    }

    /** Configure the clip shape. */
    static ClipShape with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ClipShape(config);
    }

    @Override
    public View body(View content) {
        Signal<ShapeKind> shape =
                config.shape != null ? config.shape : Signals.constant(ShapeKind.RECTANGLE);
        return Modified.props(content,
                Modified.prop(Properties.CLIPS_TO_BOUNDS, 1),
                Modified.prop(Properties.SHAPE_KIND, shape));
    }
}
