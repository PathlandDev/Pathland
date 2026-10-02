package com.pathland.demo.music;

import com.pathland.view.*;
import com.pathland.view.signal.WritableSignal;

import static com.pathland.view.signal.Signals.computed;

/**
 * The bottom player bar: an app-driven {@link Audio} node whose custom
 * {@link PlayerControlsStyle} renders the whole bar surface — a centered row of
 * {@code [⏮] [play-pause] [⏭] | [cover art] [track title] | [volume]} over a
 * slim seek bar. The audio element is hidden; the controls drive it through the
 * bound media state, and playback events (time/ended/volume) flow back into the
 * same state.
 */
public final class PlayerBar implements View {

    private final WritableSignal<Integer> trackIndex;
    private final WritableSignal<Float> position;
    private final WritableSignal<Boolean> playing;
    private final WritableSignal<Float> volume;
    private final WritableSignal<Float> seekRequest;
    private final WritableSignal<Float> volumeRequest;

    /**
     * @param trackIndex    the current track index (drives the source + now-playing content)
     * @param position      the play position (seconds) — the seek-bar DISPLAY
     * @param playing       toggled by the play/pause button
     * @param volume        the volume slider's DISPLAY (0..1)
     * @param seekRequest   the SEEK command bound to {@code MEDIA_POSITION} (user drags only)
     * @param volumeRequest the VOLUME command bound to {@code MEDIA_VOLUME} (user drags only)
     */
    public PlayerBar(WritableSignal<Integer> trackIndex, WritableSignal<Float> position,
                     WritableSignal<Boolean> playing, WritableSignal<Float> volume,
                     WritableSignal<Float> seekRequest, WritableSignal<Float> volumeRequest) {
        this.trackIndex = trackIndex;
        this.position = position;
        this.playing = playing;
        this.volume = volume;
        this.seekRequest = seekRequest;
        this.volumeRequest = volumeRequest;
    }

    @Override
    public View body() {
        var current = computed(() -> MusicPlayerView.at(trackIndex.get()));
        // The whole bar surface is the audio node's custom style: a hidden media
        // element + the centered control row + the slim seek bar (app-driven).
        // The position/volume REPORTS flow to the DISPLAY signals (onPositionReport/
        // onVolumeReport); MEDIA_POSITION/MEDIA_VOLUME are bound to the seek/volume
        // COMMANDS, so a report never echoes back as a command (spec/EVENTS.md Media).
        return Audio.of(computed(() -> current.get().audio()))
                .playing(playing)
                .position(seekRequest)
                .onPositionReport(position)
                .volume(volumeRequest)
                .onVolumeReport(volume)
                .onEnded(this::ended)
                .with(AudioStyleMod.of(PlayerControlsStyle.of(trackIndex, position, seekRequest, volume, volumeRequest)))
                .with(Background.of(MusicPlayerView.BAR_BG))
                .with(Border.of(MusicPlayerView.BAR_BORDER, 1f, 0f))
                .with(FrameMod.of(Commands.Size.FILL, MusicPlayerView.BAR_HEIGHT));
    }

    /** The media ended: advance to the next track and restart from the top. The
     *  playing state is unchanged — the DOM client resumes playback when the new
     *  source lands. */
    private void ended() {
        trackIndex.update(i -> (i + 1) % MusicPlayerView.TRACKS.size());
        position.set(0f);
        seekRequest.set(0f);
    }
}