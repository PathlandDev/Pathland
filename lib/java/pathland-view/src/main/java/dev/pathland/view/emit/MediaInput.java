package dev.pathland.view.emit;

/**
 * A host-side sink for the media events (spec/EVENTS.md Media) a bound
 * {@code MEDIA_CONTROLLER} reports: play/pause, time, duration, ended, and
 * volume. The payloads do not fit a single {@code Consumer}-shaped input,
 * so media controllers get a dedicated registry keyed by node id in
 * {@link RenderResult} (a no-op default keeps implementations small).
 */
public interface MediaInput {

    /** A {@code MEDIA_PLAY_STATE_CHANGED} event. */
    default void onPlayStateChanged(boolean playing) {}

    /** A {@code MEDIA_TIME_UPDATED} event (seconds). */
    default void onTimeUpdated(float seconds) {}

    /** A {@code MEDIA_DURATION_CHANGED} event (seconds). */
    default void onDurationChanged(float seconds) {}

    /** A {@code MEDIA_ENDED} event. */
    default void onEnded() {}

    /** A {@code MEDIA_VOLUME_CHANGED} event (0..1). */
    default void onVolumeChanged(float volume) {}
}