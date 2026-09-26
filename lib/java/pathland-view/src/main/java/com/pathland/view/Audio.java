package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;

/**
 * An audio playback node (a native element; the renderer resolves the media).
 * Playback interaction (play/pause/volume/seek) is **renderer-native** — the
 * app supplies only the source reference and size/layout.
 */
public final class Audio implements View {

    private final String source;
    private final Signal<String> sourceSignal;

    private Audio(String source, Signal<String> sourceSignal) {
        this.source = source;
        this.sourceSignal = sourceSignal;
    }

    /** An audio node with no source (the renderer's default visual). */
    public static Audio of() {
        return new Audio(null, null);
    }

    /** An audio node with a source (resource name, file path, or absolute URL). */
    public static Audio of(String source) {
        return new Audio(source, null);
    }

    /** An audio node whose source is bound to a reactive signal. */
    public static Audio of(Signal<String> source) {
        return new Audio(null, source);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.AUDIO);
        if (sourceSignal != null) {
            node.properties.put(Properties.AUDIO_SOURCE, sourceSignal.get());
            node.propertyBindings.put(Properties.AUDIO_SOURCE, sourceSignal);
        } else if (source != null) {
            node.properties.put(Properties.AUDIO_SOURCE, source);
        }
        return node;
    }
}