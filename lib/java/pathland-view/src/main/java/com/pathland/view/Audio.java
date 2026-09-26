package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

/**
 * An audio playback node (a native element; the renderer resolves the media).
 * Playback interaction (play/pause/volume/seek) is **renderer-native** — the
 * app supplies only the source reference and size/layout. The source is a
 * single {@link Signal<String>} (a static source is sugar for a constant
 * signal; a change re-emits only this node's {@code AUDIO_SOURCE}).
 */
public final class Audio implements View {

    private final Signal<String> sourceSignal;

    private Audio(Signal<String> sourceSignal) {
        this.sourceSignal = sourceSignal;
    }

    /** An audio node with no source (the renderer's default visual). */
    public static Audio of() {
        return new Audio(null);
    }

    /** An audio node with a static source (resource name, file path, or URL). */
    public static Audio of(String source) {
        return new Audio(Signals.constant(source));
    }

    /** An audio node whose source is bound to a reactive signal. */
    public static Audio of(Signal<String> source) {
        return new Audio(source);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.AUDIO);
        if (sourceSignal != null) {
            node.properties.put(Properties.AUDIO_SOURCE, sourceSignal.get());
            node.propertyBindings.put(Properties.AUDIO_SOURCE, sourceSignal);
        }
        return node;
    }
}