package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

/**
 * A continuous or stepped numeric range control (SwiftUI {@code Slider}). The value
 * binds to a {@link WritableSignal<Float>}; user drags flow back as {@code VALUE_CHANGED},
 * and an optional {@code onEditingChanged} receives {@code EDITING_CHANGED} boundaries
 * (spec/EVENTS.md in-flight interaction) — e.g. a seek bar that commits on release.
 */
public final class Slider implements View {

    private final float min;
    private final float max;
    private final WritableSignal<Float> binding;
    private final Consumer<Boolean> onEditingChanged;

    private Slider(WritableSignal<Float> binding, float min, float max,
                   Consumer<Boolean> onEditingChanged) {
        this.min = min;
        this.max = max;
        this.binding = binding;
        this.onEditingChanged = onEditingChanged;
    }

    /** A continuous numeric range control bound to {@code binding} (SwiftUI {@code Slider(value:in:)}). */
    public static Slider of(WritableSignal<Float> binding, float min, float max) {
        return of(binding, min, max, null);
    }

    /** A slider that also reports {@code EDITING_CHANGED} drag start/end to
     *  {@code onEditingChanged} (the renderers send the boundaries only when the
     *  {@code EDITING} listener bit is set, spec/EVENTS.md bit 6). */
    public static Slider of(WritableSignal<Float> binding, float min, float max,
                            Consumer<Boolean> onEditingChanged) {
        return new Slider(binding, min, max, onEditingChanged);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.SLIDER);
        node.properties.put(Properties.VALUE, binding.get());
        node.properties.put(Properties.MIN_VALUE, min);
        node.properties.put(Properties.MAX_VALUE, max);
        node.propertyBindings.put(Properties.VALUE, binding);
        node.valueInput = binding::set;
        if (onEditingChanged != null) {
            node.properties.computeIfAbsent(Properties.EVENT_LISTENERS, k -> 0);
            node.properties.put(Properties.EVENT_LISTENERS,
                    (Integer) node.properties.get(Properties.EVENT_LISTENERS) | Commands.Listeners.EDITING);
            node.editingInput = onEditingChanged;
        }
        return node;
    }
}