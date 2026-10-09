package com.pathland.demo.music;

import com.pathland.view.*;
import com.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

import static com.pathland.view.signal.Signals.computed;

/**
 * A single track row: a cover thumbnail, the title/artist, and the duration.
 * Selecting it starts that track through the app-owned player state, and the
 * current row is highlighted reactively via a computed background/foreground.
 *
 * <p>Configured with {@code TrackRowView.with(r -> r.track(...).index(...)...)} -
 * a private constructor keeps creation on the configurator (spec DSL.md §2).
 */
public final class TrackRowView implements View, Configurable<TrackRowView.Config> {

    /** {@link TrackRowView} values. */
    public static final class Config implements View.Config {

        private Track track;
        private int index;
        private WritableSignal<Integer> trackIndex;
        private WritableSignal<Float> position;
        private WritableSignal<Boolean> playing;
        private WritableSignal<Float> seekRequest;

        /** The track this row represents. */
        public Config track(Track track) {
            this.track = track;
            return this;
        }

        /** The track's index in the library. */
        public Config index(int index) {
            this.index = index;
            return this;
        }

        /** The current track index (drives the highlight). */
        public Config trackIndex(WritableSignal<Integer> trackIndex) {
            this.trackIndex = trackIndex;
            return this;
        }

        /** The play position (seconds), reset on selection. */
        public Config position(WritableSignal<Float> position) {
            this.position = position;
            return this;
        }

        /** Set to true when the row is selected. */
        public Config playing(WritableSignal<Boolean> playing) {
            this.playing = playing;
            return this;
        }

        /** The seek command signal (seconds), reset on selection so the
         *  newly-selected track starts from the beginning. */
        public Config seekRequest(WritableSignal<Float> seekRequest) {
            this.seekRequest = seekRequest;
            return this;
        }
    }

    private final Config config;

    private TrackRowView(Config config) {
        this.config = config;
    }

    /** Configure the track row's values. */
    public static ViewBuilder<TrackRowView, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new TrackRowView(config));
    }

    /** Apply modifiers to a bare track row. */
    public static ViewBuilder<TrackRowView, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new TrackRowView(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public View body() {
        var isCurrent = computed(() -> MusicPlayerView.at(config.trackIndex.get()) == config.track);
        var bg = computed(() -> isCurrent.get() ? MusicPlayerView.ACTIVE_ROW_BG : Color.CLEAR);
        var fg = computed(() -> isCurrent.get() ? MusicPlayerView.ACTIVE_ROW_FG : Color.BLACK);
        return Button
                .modifiers(
                        Background.with(b -> b.color(bg)),
                        ForegroundStyle.with(f -> f.color(fg)),
                        Padding.with(p -> p.uniform(6)),
                        // Full-width row: the layout contract's cross-axis default is hug
                        // (LAYOUT.md), so a row that should span the library column must
                        // explicitly FILL its width.
                        Frame.ofWidth(Commands.Size.FILL))
                .with(b -> b.action(() -> {
                    config.trackIndex.set(config.index);
                    config.position.set(0f);
                    config.seekRequest.set(0f);
                    config.playing.set(true);
                }))
                .children(
                        HStack
                                .with(h -> h.alignment(VerticalAlignment.CENTER).spacing(12f))
                                .children(
                                        Image.with(i -> i.source(config.track.cover())).modifiers(
                                                Frame.with(f -> f.width(44).height(44)),
                                                ScaledToFit.with()),
                                        VStack
                                                .with(v -> v.alignment(HorizontalAlignment.LEADING).spacing(2))
                                                .children(
                                                        Text.with(t -> t.text(config.track.title())).modifiers(FontWeight.SEMIBOLD),
                                                        Text.with(t -> t.text(config.track.artist() + " · " + config.track.album()))
                                                                .modifiers(
                                                                        FontSize.with(s -> s.size(13)),
                                                                        ForegroundStyle.with(f -> f.color(MusicPlayerView.SECONDARY_FG)))),
                                        Spacer.modifiers(),
                                        Text.with(t -> t.text(MusicPlayerView.fmt(config.track.duration())))
                                                .modifiers(
                                                        FontSize.with(s -> s.size(13)),
                                                        ForegroundStyle.with(f -> f.color(MusicPlayerView.SECONDARY_FG)))));
    }
}