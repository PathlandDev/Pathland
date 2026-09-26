package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

/** An image leaf (a native element; the renderer resolves the visual). The
 *  source is a single {@link Signal<String>} — a change re-emits only this
 *  node's {@code IMAGE_SOURCE}; a static source is sugar for a constant signal. */
public final class Image implements View {

    private final Signal<String> sourceSignal;

    private Image(Signal<String> sourceSignal) {
        this.sourceSignal = sourceSignal;
    }

    /** An image with no source (the renderer's default visual). */
    public static Image of() {
        return new Image(null);
    }

    /** An image with a static source (resource name, file path, or absolute URL). */
    public static Image of(String source) {
        return new Image(Signals.constant(source));
    }

    /** An image whose source is bound to a reactive signal. */
    public static Image of(Signal<String> source) {
        return new Image(source);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.IMAGE);
        if (sourceSignal != null) {
            node.properties.put(Properties.IMAGE_SOURCE, sourceSignal.get());
            node.propertyBindings.put(Properties.IMAGE_SOURCE, sourceSignal);
        }
        return node;
    }
}