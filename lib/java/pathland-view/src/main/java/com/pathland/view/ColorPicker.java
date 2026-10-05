package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.WritableSignal;

/**
 * A native system color picker ({@code ColorPicker}). The selected color binds
 * to a {@link WritableSignal<Color>}; changes flow back as {@code VALUE_CHANGED} carrying
 * the packed {@code 0xAARRGGBB} color reinterpreted as an f32 bit pattern.
 */
public final class ColorPicker implements View {

    private final WritableSignal<Color> binding;

    private ColorPicker(WritableSignal<Color> binding) {
        this.binding = binding;
    }

    /** A native color picker bound to {@code binding}. */
    public static ColorPicker of(WritableSignal<Color> binding) {
        return new ColorPicker(binding);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.COLOR_PICKER);
        node.properties.put(Properties.COLOR_VALUE, binding.get());
        node.propertyBindings.put(Properties.COLOR_VALUE, binding);
        node.valueInput = v -> binding.set(new Color(Float.floatToRawIntBits(v), null));
        return node;
    }
}