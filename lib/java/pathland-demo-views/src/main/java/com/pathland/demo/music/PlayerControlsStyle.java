package com.pathland.demo.music;

import com.pathland.view.*;
import com.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

import static com.pathland.view.signal.Signals.computed;

/**
 * The music player's custom {@link AudioStyle}: the full bottom-bar surface —
 * a centered row of {@code [rewind] [play-pause] [forward] | [cover art]
 * [track title] | [volume]} over a slim, draggable seek bar that reports
 * <b>progress as a percent</b> (mapped in the UI model via {@link PercentSeek};
 * the wire keeps seconds). Everything binds to the media configuration's
 * signals, so this becomes the {@code AUDIO} node's children and the renderer
 * emits a hidden media element alongside these app-defined controls (real
 * playback, app-driven).
 *
 * <p>Configured via {@code PlayerControlsStyle.with(s -> s.trackIndex(...)…)} —
 * a private constructor keeps creation on the configurator (spec DSL.md §2).
 */
public final class PlayerControlsStyle implements AudioStyle {

    /** {@link PlayerControlsStyle} values: the DISPLAY signals (fed by the media
     *  reports) and the SEEK/VOLUME command signals (written on user drags). */
    public static final class Config implements View.Config {

        private WritableSignal<Integer> trackIndex;
        private WritableSignal<Float> position;
        private WritableSignal<Float> seekRequest;
        private WritableSignal<Float> volume;
        private WritableSignal<Float> volumeRequest;

        /** The current track index. */
        public Config trackIndex(WritableSignal<Integer> trackIndex) {
            this.trackIndex = trackIndex;
            return this;
        }

        /** The play-position DISPLAY signal (fed by the media reports). */
        public Config position(WritableSignal<Float> position) {
            this.position = position;
            return this;
        }

        /** The SEEK command signal (written on user drags). */
        public Config seekRequest(WritableSignal<Float> seekRequest) {
            this.seekRequest = seekRequest;
            return this;
        }

        /** The volume DISPLAY signal (fed by the media reports). */
        public Config volume(WritableSignal<Float> volume) {
            this.volume = volume;
            return this;
        }

        /** The VOLUME command signal (written on user drags). */
        public Config volumeRequest(WritableSignal<Float> volumeRequest) {
            this.volumeRequest = volumeRequest;
            return this;
        }
    }

    private final Config config;

    private PlayerControlsStyle(Config config) {
        this.config = config;
    }

    /** Configure the player-controls style's signals. */
    public static PlayerControlsStyle with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new PlayerControlsStyle(config);
    }

    @Override
    public View makeBody(Configuration configuration) {
        var current = computed(() -> MusicPlayerView.at(config.trackIndex.get()));
        var transportIcon = computed(() -> configuration.playing().get() ? IconName.PAUSE.wire() : IconName.PLAY.wire());
        var seek = new SeekControl(config.position, config.seekRequest);
        var volumeControl = new VolumeControl(config.volume, config.volumeRequest);

        var playControl = HStack
                .with(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f))
                .children(
                        Button
                                .modifiers(FontSize.with(20))
                                .with(b -> b.action(() -> { prev(); restartSeek(); }))
                                .children(Icon.with(IconName.SKIP_BACK)),
                        Button
                                .modifiers(FontSize.with(28))
                                .with(b -> b.action(() -> configuration.playing().update(v -> !v)))
                                .children(Icon.with(transportIcon)),
                        Button
                                .modifiers(FontSize.with(20))
                                .with(b -> b.action(() -> { next(); restartSeek(); }))
                                .children(Icon.with(IconName.SKIP_FORWARD)));
        var trackInfo = HStack
                .with(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f))
                .children(
                        Image.with(computed(() -> current.get().cover())).modifiers(
                                Frame.with(f -> f.width(36).height(36)),
                                ScaledToFit.with()),
                        VStack
                                .modifiers(Frame.with(f -> f.width(160f)))
                                .with(v -> v.alignment(HorizontalAlignment.LEADING).spacing(2))
                                .children(
                                        Text.with(computed(() -> current.get().title()))
                                                .modifiers(FontWeight.SEMIBOLD, LineLimit.with(1)),
                                        Text.with(computed(() -> current.get().artist()))
                                                .modifiers(
                                                        FontSize.with(13),
                                                        ForegroundStyle.with(MusicPlayerView.SECONDARY_FG),
                                                        LineLimit.with(1))));

        var volumeView = HStack
                .with(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f))
                .children(
                        Icon.with(IconName.VOLUME).modifiers(FontSize.with(16)),
                        Slider.with(s -> s.value(volumeControl).in(0f, 1f)));

        var compactControls = HStack
                .with(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f))
                .children(
                        playControl,
                        Text.with("|").modifiers(FontSize.with(18), ForegroundStyle.with(MusicPlayerView.SECONDARY_FG)),
                        trackInfo);

        var controls = HStack
                .with(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f))
                .children(
                        playControl,
                        Text.with("|").modifiers(FontSize.with(18), ForegroundStyle.with(MusicPlayerView.SECONDARY_FG)),
                        trackInfo,
                        Text.with("|").modifiers(FontSize.with(18), ForegroundStyle.with(MusicPlayerView.SECONDARY_FG)),
                        volumeView);

        var groups = SizeThatFits.with(
                Fit.of(controls, 600),
                Fit.of(compactControls)
        );

        return VStack
                .with(v -> v.alignment(HorizontalAlignment.CENTER).spacing(0))
                .children(
                        // Centered groups over a full-width seek bar (the slider's FILL
                        // width spans the bar via fill propagation; the groups stay
                        // centered above it).
                        groups,
                        Slider.with(s -> s.value(new PercentSeek(seek,
                                        () -> MusicPlayerView.at(config.trackIndex.get()).duration()))
                                .in(0f, 100f).onEditingChanged(seek::onEditingChanged))
                                .modifiers(
                                        Frame.with(f -> f.width(Float.POSITIVE_INFINITY)),
                                        Padding.with(p -> p.edges(0, 16, 0, 16))));
    }

    /** Previous track (wraps around); the seek restarts via the command binding. */
    private void prev() {
        config.trackIndex.update(i -> (i - 1 + MusicPlayerView.TRACKS.size()) % MusicPlayerView.TRACKS.size());
    }

    /** Next track (wraps around); the seek restarts via the command binding. */
    private void next() {
        config.trackIndex.update(i -> (i + 1) % MusicPlayerView.TRACKS.size());
    }

    /** A prev/next restart seeks to the top: reset both the display and the command. */
    private void restartSeek() {
        config.position.set(0f);
        config.seekRequest.set(0f);
    }
}