package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

/**
 * A semantic icon (the {@code ICON} primitive): a canonical {@link IconName}
 * written once in the UI model and mapped by each renderer to its native icon
 * set. The name is a single
 * {@link Signal<String>}; a change re-emits only this node's {@code ICON_NAME}.
 *
 * <p>Size follows {@code FONT_SIZE} (text-style, like SwiftUI symbol sizing) and
 * tint follows {@code COLOR}/{@code foregroundStyle}; an
 * {@code accessibilityLabel} makes the icon presentable to screen readers.
 */
public final class Icon implements View {

    private final Signal<String> nameSignal;
    private final String label;

    private Icon(Signal<String> nameSignal, String label) {
        this.nameSignal = nameSignal;
        this.label = label;
    }

    /** A static canonical icon. */
    public static Icon of(IconName name) {
        return new Icon(Signals.constant(name.wire()), null);
    }

    /** A static canonical icon by canonical name (for names outside the enum). */
    public static Icon of(String canonicalName) {
        return new Icon(Signals.constant(canonicalName), null);
    }

    /** A reactive icon bound to a canonical-name signal. */
    public static Icon of(Signal<String> canonicalName) {
        return new Icon(canonicalName, null);
    }

    /** A static canonical icon with an accessibility label. */
    public static Icon labeled(IconName name, String label) {
        return new Icon(Signals.constant(name.wire()), label);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.ICON);
        if (nameSignal != null) {
            node.properties.put(Properties.ICON_NAME, nameSignal.get());
            node.propertyBindings.put(Properties.ICON_NAME, nameSignal);
        }
        if (label != null && !label.isBlank()) {
            node.properties.put(Properties.LABEL, label);
        }
        return node;
    }
}