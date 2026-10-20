package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

/**
 * A native system color picker ({@code ColorPicker}). The selected color binds
 * to a {@link WritableSignal<Color>}; changes flow back as {@code VALUE_CHANGED} carrying
 * the packed {@code 0xAARRGGBB} color reinterpreted as an f32 bit pattern.
 */
public final class ColorPicker implements View, Configurable<ColorPicker.Config> {

    /** {@link ColorPicker} values. */
    public static final class Config implements View.Config {

        private WritableSignal<Color> binding;

        /** Bind the selected color (two-way). */
        public Config selection(WritableSignal<Color> binding) {
            this.binding = binding;
            return this;
        }
    }

    private final Config config;

    private ColorPicker(Config config) {
        this.config = config;
    }

    /** Configure the color picker's values. */
/** An empty ColorPicker (no values). */
    public static ViewBuilder<ColorPicker, Config> make() {
        return new ViewBuilder<>(new ColorPicker(new Config()));
    }

    public static ViewBuilder<ColorPicker, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new ColorPicker(config));
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.COLOR_PICKER);
        if (config.binding != null) {
            node.properties.put(Properties.COLOR_VALUE, config.binding.get());
            node.propertyBindings.put(Properties.COLOR_VALUE, config.binding);
            node.valueInput = v -> config.binding.set(new Color(Float.floatToRawIntBits(v), null));
        }
        return node;
    }
}
