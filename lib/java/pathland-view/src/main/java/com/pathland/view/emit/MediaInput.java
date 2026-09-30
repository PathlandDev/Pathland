package com.pathland.view.emit;

/**
 * A host-side sink for the media events (spec/EVENTS.md Media) a bound
 * {@code AUDIO}/{@code VIDEO} node reports: play/pause, time, ended, and
 * volume. The four payloads do not fit a single {@code Consumer}-shaped input,
 * so media nodes get a dedicated registry keyed by node id in
 * {@link RenderResult} (a no-op default keeps implementations small).
 */
public interface MediaInput {

    /** A {@code MEDIA_PLAY_STATE_CHANGED} event. */
    default void onPlayStateChanged(boolean playing) {}

    /** A {@code MEDIA_TIME_UPDATED} event (seconds). */
    default void onTimeUpdated(float seconds) {}

    /** A {@code MEDIA_ENDED} event. */
    default void onEnded() {}

    /** A {@code MEDIA_VOLUME_CHANGED} event (0..1). */
    default void onVolumeChanged(float volume) {}
}