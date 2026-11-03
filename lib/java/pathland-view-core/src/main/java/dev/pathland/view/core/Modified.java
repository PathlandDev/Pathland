package dev.pathland.view.core;

import dev.pathland.view.engine.PathlandNode;
import dev.pathland.view.engine.ReactiveValues;
import dev.pathland.view.core.signal.Signal;

import java.util.List;

/**
 * A view wrapped by one or more property modifiers (innermost-first) or a custom
 * {@link ViewModifier}. Property modifiers apply to the innermost built node and are
 * overwritten by outer modifiers ("outermost wins"); reactive modifiers record
 * a node-level binding so a signal change re-emits only that property.
 */
public final class Modified implements View {

    /** One property application: a static value or a reactive signal. */
    public record Prop(int property, Object value, Signal<?> signal) {

        public static Prop value(int property, Object value) {
            return new Prop(property, value, null);
        }

        public static Prop reactive(int property, Signal<?> signal) {
            return new Prop(property, null, signal);
        }
    }

    private final View inner;
    private final List<Prop> props;
    private final ViewModifier modifier;

    private Modified(View inner, List<Prop> props, ViewModifier modifier) {
        this.inner = inner;
        this.props = props;
        this.modifier = modifier;
    }

    /** A static property mod. */
    public static Prop prop(int property, Object value) {
        return Prop.value(property, value);
    }

    /** A reactive property mod. */
    public static Prop prop(int property, Signal<?> signal) {
        return Prop.reactive(property, signal);
    }

    /** The wrapped view (the next link inward in this modifier chain). */
    public View inner() {
        return inner;
    }

    /** Build a property-modifier view from inner + props. */
    public static Modified props(View inner, Prop... props) {
        return new Modified(inner, List.of(props), null);
    }

    /** A custom-view-modifier wrapper. */
    public static Modified apply(View inner, ViewModifier modifier) {
        return new Modified(inner, List.of(), modifier);
    }

    @Override
    public PathlandNode renderNode(Environment env) {
        if (modifier != null) {
            return modifier.body(inner).renderNode(env);
        }
        PathlandNode node = inner.renderNode(env);
        for (Prop p : props) {
            if (p.signal() != null) {
                Object value = ReactiveValues.initial(p.property(), p.signal());
                node.properties.put(p.property(), value);
                node.propertyBindings.put(p.property(), p.signal());
            } else {
                node.properties.put(p.property(), p.value());
            }
        }
        return node;
    }
}