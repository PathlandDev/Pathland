package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;

/**
 * A video playback node (a native element; the renderer resolves the media).
 * Playback interaction (play/pause/volume/seek) is **renderer-native** — the
 * app supplies only the source reference and size/layout. A poster/preview
 * frame is a planned draft (`POSTER_SOURCE`).
 */
public final class Video implements View {

    private final String source;
    private final Signal<String> sourceSignal;

    private Video(String source, Signal<String> sourceSignal) {
        this.source = source;
        this.sourceSignal = sourceSignal;
    }

    /** A video node with no source (the renderer's default visual). */
    public static Video of() {
        return new Video(null, null);
    }

    /** A video node with a source (resource name, file path, or absolute URL). */
    public static Video of(String source) {
        return new Video(source, null);
    }

    /** A video node whose source is bound to a reactive signal. */
    public static Video of(Signal<String> source) {
        return new Video(null, source);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.VIDEO);
        if (sourceSignal != null) {
            node.properties.put(Properties.VIDEO_SOURCE, sourceSignal.get());
            node.propertyBindings.put(Properties.VIDEO_SOURCE, sourceSignal);
        } else if (source != null) {
            node.properties.put(Properties.VIDEO_SOURCE, source);
        }
        return node;
    }
}