package com.pathland.demo.music;

import com.pathland.view.*;
import com.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

import static com.pathland.view.signal.Signals.computed;

/**
 * The bottom player bar: an app-driven {@link Audio} node whose custom
 * {@link PlayerControlsStyle} renders the whole bar surface — a centered row of
 * {@code [⏮] [play-pause] [⏭] | [cover art] [track title] | [volume]} over a
 * slim seek bar. The audio element is hidden; the controls drive it through the
 * bound media state, and playback events (time/ended/volume) flow back into the
 * same state.
 *
 * <p>Configured with {@code PlayerBar.with(p -> p.trackIndex(...).position(...)...)} —
 * a private constructor keeps creation on the configurator (spec DSL.md §2).
 */
public final class PlayerBar implements View, Configurable<PlayerBar.Config> {

    /** {@link PlayerBar} values. */
    public static final class Config implements View.Config {

        private WritableSignal<Integer> trackIndex;
        private WritableSignal<Float> position;
        private WritableSignal<Boolean> playing;
        private WritableSignal<Float> volume;
        private WritableSignal<Float> seekRequest;
        private WritableSignal<Float> volumeRequest;

        /** The current track index (drives the source + now-playing content). */
        public Config trackIndex(WritableSignal<Integer> trackIndex) {
            this.trackIndex = trackIndex;
            return this;
        }

        /** The play position (seconds) — the seek-bar DISPLAY. */
        public Config position(WritableSignal<Float> position) {
            this.position = position;
            return this;
        }

        /** Toggled by the play/pause button. */
        public Config playing(WritableSignal<Boolean> playing) {
            this.playing = playing;
            return this;
        }

        /** The volume slider's DISPLAY (0..1). */
        public Config volume(WritableSignal<Float> volume) {
            this.volume = volume;
            return this;
        }

        /** The SEEK command bound to {@code MEDIA_POSITION} (user drags only). */
        public Config seekRequest(WritableSignal<Float> seekRequest) {
            this.seekRequest = seekRequest;
            return this;
        }

        /** The VOLUME command bound to {@code MEDIA_VOLUME} (user drags only). */
        public Config volumeRequest(WritableSignal<Float> volumeRequest) {
            this.volumeRequest = volumeRequest;
            return this;
        }
    }

    private final Config config;

    private PlayerBar(Config config) {
        this.config = config;
    }

    /** Configure the player bar's values. */
    public static ViewBuilder<PlayerBar, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new PlayerBar(config));
    }

    /** Apply modifiers to a bare player bar. */
    public static ViewBuilder<PlayerBar, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new PlayerBar(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public View body() {
        var current = computed(() -> MusicPlayerView.at(config.trackIndex.get()));
        // The whole bar surface is the audio node's custom style: a hidden media
        // element + the centered control row + the slim seek bar (app-driven).
        // The position/volume REPORTS flow to the DISPLAY signals (onPositionReport/
        // onVolumeReport); MEDIA_POSITION/MEDIA_VOLUME are bound to the seek/volume
        // COMMANDS, so a report never echoes back as a command (spec/EVENTS.md Media).
        return Audio
                .modifiers(
                        PlayerControlsStyle.with(s -> s
                                .trackIndex(config.trackIndex)
                                .position(config.position)
                                .seekRequest(config.seekRequest)
                                .volume(config.volume)
                                .volumeRequest(config.volumeRequest)),
                        Background.with(b -> b.color(MusicPlayerView.BAR_BG)),
                        Frame.with(f -> f.width(Commands.Size.FILL).height(MusicPlayerView.BAR_HEIGHT)))
                .with(a -> a
                        .source(computed(() -> current.get().audio()))
                        .playing(config.playing)
                        .position(config.seekRequest)
                        .onPositionReport(config.position)
                        .volume(config.volumeRequest)
                        .onVolumeReport(config.volume)
                        .onEnded(this::ended));
    }

    /** The media ended: advance to the next track and restart from the top. The
     *  playing state is unchanged — the DOM client resumes playback when the new
     *  source lands. */
    private void ended() {
        config.trackIndex.update(i -> (i + 1) % MusicPlayerView.TRACKS.size());
        config.position.set(0f);
        config.seekRequest.set(0f);
    }
}