package dev.pathland.demo.music;

import dev.pathland.view.*;
import dev.pathland.view.signal.WritableSignal;
import static dev.pathland.view.Modifiers.*;

import java.util.function.Consumer;

import static dev.pathland.view.signal.Signals.computed;

/**
 * The player bar's control surface: a centered row of
 * {@code [rewind] [play-pause] [forward] | [cover art] [track title] | [volume]}
 * over a slim, draggable seek bar that reports <b>progress as a percent</b>
 * (mapped in the UI model via {@link PercentSeek}; the wire keeps seconds).
 *
 * <p><b>Audio has no view</b> (spec/MEDIA.md): this is an ordinary view whose
 * controls bind a {@link MediaController}'s transport signals — the play button
 * toggles {@code playing()}, the seek bar displays {@code time()} and writes
 * {@code position()}, the volume slider displays {@code volumeReport()} and writes
 * {@code volume()}. The controller owns the playback; a {@code SIZE_THAT_FITS}
 * swap of this bar never restarts it.
 *
 * <p>Configured via {@code PlayerControls.make(c -> c.player(player).trackIndex(i))}.
 */
public final class PlayerControls implements View, Configurable<PlayerControls.Config> {

    /** {@link PlayerControls} values. */
    public static final class Config implements View.Config {

        private MediaController player;
        private WritableSignal<Integer> trackIndex;

        /** The playback controller the controls drive. */
        public Config player(MediaController player) {
            this.player = player;
            return this;
        }

        /** The current track index (drives the track info + prev/next). */
        public Config trackIndex(WritableSignal<Integer> trackIndex) {
            this.trackIndex = trackIndex;
            return this;
        }
    }

    private final Config config;

    private PlayerControls(Config config) {
        this.config = config;
    }

    /** Configure the control surface's values. */
    public static ViewBuilder<PlayerControls, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new PlayerControls(config));
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public View body() {
        MediaController player = config.player;
        var current = computed(() -> MusicPlayerView.at(config.trackIndex.get()));
        var transportIcon = computed(() -> player.playing().get() ? IconName.PAUSE.wire() : IconName.PLAY.wire());
        var seek = new SeekControl(player.time(), player.position());
        var volumeControl = new VolumeControl(player.volumeReport(), player.volume());

        var playControl = HStack
                .make(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f))
                .children(
                        Button.make(b -> b.action(this::prev))
                                .modifiers(fontSize(20), accessibilityLabel("Previous track"))
                                .children(Icon.make(IconName.SKIP_BACK)),
                        Button.make(b -> b.action(player::toggle))
                                .modifiers(fontSize(20), accessibilityLabel(computed(
                                        () -> player.playing().get() ? "Pause" : "Play")))
                                .children(Icon.make(transportIcon)),
                        Button.make(b -> b.action(this::next))
                                .modifiers(fontSize(20), accessibilityLabel("Next track"))
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
                        Slider.make(s -> s.value(volumeControl).in(0f, 1f))
                                .modifiers(accessibilityLabel("Volume")));

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
                Fit.of(controls, 700),
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
                                        padding(p -> p.edges(0, 16, 0, 16)),
                                        accessibilityLabel("Seek")));
    }

    /** Previous track (wraps around), restarting from the top. */
    private void prev() {
        config.trackIndex.update(i -> (i - 1 + MusicPlayerView.TRACKS.size()) % MusicPlayerView.TRACKS.size());
        config.player.seek(0f);
    }

    /** Next track (wraps around), restarting from the top. */
    private void next() {
        config.trackIndex.update(i -> (i + 1) % MusicPlayerView.TRACKS.size());
        config.player.seek(0f);
    }
}
