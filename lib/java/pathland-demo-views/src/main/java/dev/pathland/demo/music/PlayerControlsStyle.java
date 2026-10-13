package dev.pathland.demo.music;

import dev.pathland.view.*;
import dev.pathland.view.signal.WritableSignal;
import static dev.pathland.view.Modifiers.*;

import java.util.function.Consumer;

import static dev.pathland.view.signal.Signals.computed;

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
 * <p>Configured via {@code playerControlsStyle(s -> s.trackIndex(...)…)} — an
 * app-authored style exposes a lowercase static factory on its own type (spec
 * DSL.md §5.6); a private constructor keeps creation on the configurator.
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
    public static PlayerControlsStyle playerControlsStyle(Consumer<Config> configure) {
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
                .make(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f))
                .children(
                        Button.make(b -> b.action(() -> { prev(); restartSeek(); })).modifiers(fontSize(20))
                                        .children(Icon.make(IconName.SKIP_BACK)),
                        Button.make(b -> b.action(() -> configuration.playing().update(v -> !v))).modifiers(fontSize(28))
                                        .children(Icon.make(transportIcon)),
                        Button.make(b -> b.action(() -> { next(); restartSeek(); })).modifiers(fontSize(20))
                                        .children(Icon.make(IconName.SKIP_FORWARD)));
        var trackInfo = HStack
                .make(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f))
                .children(
                        Image.make(computed(() -> current.get().cover())).modifiers(
                                frame(f -> f.width(36).height(36)),
                                scaledToFit()),
                        VStack.make(v -> v.alignment(HorizontalAlignment.LEADING).spacing(2)).modifiers(frame(f -> f.width(160f)))
                                .children(
                                        Text.make(computed(() -> current.get().title()))
                                                .modifiers(FontWeight.SEMIBOLD, lineLimit(1)),
                                        Text.make(computed(() -> current.get().artist()))
                                                .modifiers(
                                                        fontSize(13),
                                                        foregroundStyle(MusicPlayerView.SECONDARY_FG),
                                                        lineLimit(1))));

        var volumeView = HStack
                .make(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f))
                .children(
                        Icon.make(IconName.VOLUME).modifiers(fontSize(16)),
                        Slider.make(s -> s.value(volumeControl).in(0f, 1f)));

        var compactControls = HStack
                .make(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f))
                .children(
                        playControl,
                        Text.make("|").modifiers(fontSize(18), foregroundStyle(MusicPlayerView.SECONDARY_FG)),
                        trackInfo);

        var controls = HStack
                .make(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f))
                .children(
                        playControl,
                        Text.make("|").modifiers(fontSize(18), foregroundStyle(MusicPlayerView.SECONDARY_FG)),
                        trackInfo,
                        Text.make("|").modifiers(fontSize(18), foregroundStyle(MusicPlayerView.SECONDARY_FG)),
                        volumeView);

        var groups = SizeThatFits.make(
                Fit.of(controls, 600),
                Fit.of(compactControls)
        );

        return VStack
                .make(v -> v.alignment(HorizontalAlignment.CENTER).spacing(0))
                .children(
                        // Centered groups over a full-width seek bar (the slider's FILL
                        // width spans the bar via fill propagation; the groups stay
                        // centered above it).
                        groups,
                        Slider.make(s -> s.value(new PercentSeek(seek,
                                        () -> MusicPlayerView.at(config.trackIndex.get()).duration()))
                                .in(0f, 100f).onEditingChanged(seek::onEditingChanged))
                                .modifiers(
                                        frame(f -> f.width(Float.POSITIVE_INFINITY)),
                                        padding(p -> p.edges(0, 16, 0, 16))));
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