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

    /**
     * @param trackIndex the current track index (drives the source + now-playing content)
     * @param position   the play position (seconds), bound to the slim seek bar
     * @param playing    toggled by the play/pause button
     * @param volume     bound to the volume slider (0..1)
     */
    public PlayerBar(WritableSignal<Integer> trackIndex, WritableSignal<Float> position,
                     WritableSignal<Boolean> playing, WritableSignal<Float> volume) {
        this.trackIndex = trackIndex;
        this.position = position;
        this.playing = playing;
        this.volume = volume;
    }

    @Override
    public View body() {
        var current = computed(() -> MusicPlayerView.at(trackIndex.get()));
        // The whole bar surface is the audio node's custom style: a hidden media
        // element + the centered control row + the slim seek bar (app-driven).
        return Audio.of(computed(() -> current.get().audio()))
                .playing(playing)
                .position(position)
                .volume(volume)
                .onEnded(this::ended)
                .with(AudioStyleMod.of(PlayerControlsStyle.of(trackIndex)))
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
    }
}