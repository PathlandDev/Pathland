package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;

/** An image leaf (a native element; the renderer resolves the visual). */
public final class Image implements View {

    private final String source;
    private final Signal<String> sourceSignal;

    private Image(String source, Signal<String> sourceSignal) {
        this.source = source;
        this.sourceSignal = sourceSignal;
    }

    /** An image with no source (the renderer's default visual). */
    public static Image of() {
        return new Image(null, null);
    }

    /** An image with a source (resource name, file path, or absolute URL). */
    public static Image of(String source) {
        return new Image(source, null);
    }

    /**
     * An image whose source is bound to a reactive signal: a change re-emits only this
     * node's {@code IMAGE_SOURCE}.
     */
    public static Image of(Signal<String> source) {
        return new Image(null, source);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.IMAGE);
        if (sourceSignal != null) {
            node.properties.put(Properties.IMAGE_SOURCE, sourceSignal.get());
            node.propertyBindings.put(Properties.IMAGE_SOURCE, sourceSignal);
        } else if (source != null) {
            node.properties.put(Properties.IMAGE_SOURCE, source);
        }
        return node;
    }
}