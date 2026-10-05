package com.pathland.view;

import com.pathland.view.signal.WritableSignal;

/**
 * A video style ({@code VideoStyle}). A style turns a {@link Video}
 * node's media configuration into a body view, mirroring {@link AudioStyle}.
 * Injected down the tree via {@code View.videoStyle(VideoStyle)}.
 */
public interface VideoStyle extends Style {

    /** Build the styled content for {@code config}. */
    View makeBody(Configuration config);

    /**
     * The media configuration of the video being styled (same shape as
     * {@link AudioStyle.Configuration}); the source rides the {@code Video} view.
     */
    record Configuration(WritableSignal<Boolean> playing, WritableSignal<Float> position,
                         WritableSignal<Float> volume, float duration) {}
}