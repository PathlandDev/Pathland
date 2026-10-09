package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.WritableSignal;

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

    /** A native color picker bound to {@code binding}. */
    public static ColorPicker of(WritableSignal<Color> binding) {
        return new ColorPicker(new Config().selection(binding));
    }

    /** Configure the color picker's values. */
    public static ViewBuilder<ColorPicker, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new ColorPicker(config));
    }

    /** Apply modifiers to a bare color picker. */
    public static ViewBuilder<ColorPicker, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new ColorPicker(new Config())).modifiers(modifiers);
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
