package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

/**
 * A video playback node (a native element; the renderer resolves the media).
 * Playback interaction (play/pause/volume/seek) is **renderer-native** — the
 * app supplies only the source reference and size/layout. The source is a
 * single {@link Signal<String>} (a static source is sugar for a constant
 * signal; a change re-emits only this node's {@code VIDEO_SOURCE}). A
 * poster/preview frame is a planned draft ({@code POSTER_SOURCE}).
 */
public final class Video implements View {

    private final Signal<String> sourceSignal;

    private Video(Signal<String> sourceSignal) {
        this.sourceSignal = sourceSignal;
    }

    /** A video node with no source (the renderer's default visual). */
    public static Video of() {
        return new Video(null);
    }

    /** A video node with a static source (resource name, file path, or URL). */
    public static Video of(String source) {
        return new Video(Signals.constant(source));
    }

    /** A video node whose source is bound to a reactive signal. */
    public static Video of(Signal<String> source) {
        return new Video(source);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.VIDEO);
        if (sourceSignal != null) {
            node.properties.put(Properties.VIDEO_SOURCE, sourceSignal.get());
            node.propertyBindings.put(Properties.VIDEO_SOURCE, sourceSignal);
        }
        return node;
    }
}