package com.pathland.view;

/** The default audio style: no content — the {@link Audio} control renders its
 *  native {@code AUDIO} shell and the renderer shows native media controls
 *  ({@code <audio controls>}); the app supplies only the source. */
public enum NativeAudioStyle implements AudioStyle {

    INSTANCE;

    @Override
    public View makeBody(Configuration config) {
        return EmptyContent.INSTANCE;
    }
}