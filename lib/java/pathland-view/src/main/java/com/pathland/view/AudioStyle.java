package com.pathland.view;

import com.pathland.view.signal.WritableSignal;

/**
 * An audio style (SwiftUI {@code AudioStyle}). A style turns an {@link Audio}
 * node's media configuration (its playback signals) into a body view. Styles are
 * injected down the tree via {@code View.audioStyle(AudioStyle)}.
 *
 * <p>The default {@link NativeAudioStyle} contributes no control children — the
 * renderer shows native controls. A custom style returns the app-defined control
 * UI (transport buttons, seek/volume sliders) bound to the configuration's
 * signals; those children become the {@code AUDIO} node's children, so the
 * renderer emits a hidden media element + the custom controls.
 */
public interface AudioStyle {

    /** Build the styled body for {@code config}. */
    View makeBody(Configuration config);

    /**
     * The media configuration of the audio being styled: the playback signals a
     * custom control UI binds to. The source rides the {@code Audio} view itself.
     *
     * @param playing  the play/pause state (writable — a control toggles it)
     * @param position the play position in seconds (writable — a seek slider)
     * @param volume   the volume 0..1 (writable — a volume slider)
     * @param duration the media length in seconds (the seek slider's maximum)
     */
    record Configuration(WritableSignal<Boolean> playing, WritableSignal<Float> position,
                         WritableSignal<Float> volume, float duration) {}
}