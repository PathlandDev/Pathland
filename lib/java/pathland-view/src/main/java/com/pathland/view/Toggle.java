package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.WritableSignal;

/**
 * A boolean switch, checkbox, or toggle button (SwiftUI {@code Toggle}). The visual
 * variant is the {@code TOGGLE_STYLE} token ({@code Switch}/{@code Checkbox}/{@code Button}).
 * The checked state binds to a {@link WritableSignal<Boolean>} (its current value is the
 * initial state); user changes flow back as {@code VALUE_CHANGED} (0/1) and are written
 * into the binding.
 */
public final class Toggle implements View {

    private final ToggleStyle style;
    private final String label;
    private final WritableSignal<Boolean> binding;

    private Toggle(ToggleStyle style, WritableSignal<Boolean> binding, String label) {
        this.style = style;
        this.binding = binding;
        this.label = label;
    }

    /** A toggle with a style token, a binding, and a label. */
    public static Toggle of(ToggleStyle style, WritableSignal<Boolean> binding, String label) {
        return new Toggle(style, binding, label);
    }

    /** A {@code Switch}-style toggle with no label. */
    public static Toggle of(WritableSignal<Boolean> binding) {
        return new Toggle(ToggleStyle.SWITCH, binding, null);
    }

    /** A {@code Switch}-style toggle with a label (SwiftUI {@code Toggle("label", isOn:)}). */
    public static Toggle of(String label, WritableSignal<Boolean> binding) {
        return new Toggle(ToggleStyle.SWITCH, binding, label);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.TOGGLE);
        node.properties.put(Properties.TOGGLE_STYLE, (float) style.wire());
        node.properties.put(Properties.SELECTED, binding.get() ? 1 : 0);
        node.propertyBindings.put(Properties.SELECTED, binding);
        if (label != null) {
            node.text = label;
        }
        node.valueInput = v -> binding.set(v > 0f);
        return node;
    }
}