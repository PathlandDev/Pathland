package com.pathland.view;

import com.pathland.view.signal.WritableSignal;

/**
 * An audio style ({@code AudioStyle}). A style turns an {@link Audio}
 * node's media configuration (its playback signals) into a body view. Styles are
 * injected down the tree via {@code View.audioStyle(AudioStyle)}.
 *
 * <p>The default {@link NativeAudioStyle} contributes no content (the
 * {@link EmptyContent} sentinel) — the renderer shows native controls. A custom
 * style returns the app-defined control UI (transport buttons, seek/volume
 * sliders) bound to the configuration's signals; the {@link Audio} control
 * attaches it as the {@code AUDIO} node's child, so the renderer emits a hidden
 * media element + the custom controls (spec DSL.md §5.7).
 */
public interface AudioStyle extends Style {

    /** Build the styled content for {@code config}. */
    View makeBody(Configuration config);

    /** Scopes this style down the wrapped subtree ({@code .modifiers(AudioStyle)}). */
    @Override
    default View body(View content) {
        return content.environment(Environment.AUDIO_STYLE, this);
    }

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