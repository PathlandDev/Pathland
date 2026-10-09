package com.pathland.demo.music;

import com.pathland.view.*;
import com.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

import static com.pathland.view.signal.Signals.computed;

/**
 * The now-playing sidebar (right): the current track's large album art, its
 * title/artist, and the next two tracks. Everything is bound to the current
 * {@code trackIndex}, so a track change re-emits only this pane's deltas.
 *
 * <p>Configured with {@code NowPlayingSidebar.with(s -> s.trackIndex(...))} — a
 * private constructor keeps creation on the configurator (spec DSL.md §2).
 */
public final class NowPlayingSidebar implements View, Configurable<NowPlayingSidebar.Config> {

    /** {@link NowPlayingSidebar} values. */
    public static final class Config implements View.Config {

        private WritableSignal<Integer> trackIndex;

        /** The current track index (drives the art + up-next). */
        public Config trackIndex(WritableSignal<Integer> trackIndex) {
            this.trackIndex = trackIndex;
            return this;
        }
    }

    private final Config config;

    private NowPlayingSidebar(Config config) {
        this.config = config;
    }

    /** Configure the sidebar's values. */
    public static ViewBuilder<NowPlayingSidebar, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new NowPlayingSidebar(config));
    }

    /** Apply modifiers to a bare sidebar. */
    public static ViewBuilder<NowPlayingSidebar, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new NowPlayingSidebar(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public View body() {
        return VStack
                .modifiers(
                        Padding.with(p -> p.uniform(24)),
                        Background.with(b -> b.color(MusicPlayerView.SIDEBAR_BG)),
                        Frame.with(f -> f.width(MusicPlayerView.SIDEBAR_WIDTH).height(Commands.Size.FILL)))
                .with(v -> v.alignment(HorizontalAlignment.LEADING).spacing(14))
                .children(
                        Text.with(t -> t.text("Now Playing")).modifiers(
                                FontSize.with(s -> s.size(18)), FontWeight.BOLD, AccessibilityRole.with(a -> a.role(Roles.HEADER))),
                        Image.with(i -> i.source(computed(() -> MusicPlayerView.at(config.trackIndex.get()).cover()))).modifiers(
                                Frame.with(f -> f.width(220).height(220)),
                                ScaledToFill.with(),
                                Border.with(b -> b.color(Color.BLACK).width(1).radius(8))),
                        Text.with(t -> t.text(computed(() -> MusicPlayerView.at(config.trackIndex.get()).title())))
                                .modifiers(FontSize.with(s -> s.size(20)), FontWeight.BOLD),
                        Text.with(t -> t.text(computed(() -> MusicPlayerView.at(config.trackIndex.get()).artist()
                                + " · " + MusicPlayerView.at(config.trackIndex.get()).album())))
                                .modifiers(ForegroundStyle.with(f -> f.color(MusicPlayerView.SECONDARY_FG))),
                        Divider.modifiers(Padding.with(p -> p.edges(0, 0, 0, 10))),
                        Text.with(t -> t.text("Up Next")).modifiers(FontWeight.SEMIBOLD),
                        upNextRow(1),
                        upNextRow(2));
    }

    /** The track {@code offset} places after the current one. */
    private View upNextRow(int offset) {
        return Text.with(t -> t.text(computed(() -> {
            Track track = MusicPlayerView.at(config.trackIndex.get() + offset);
            return (offset == 1 ? "Up next · " : "Then · ") + track.title() + " — " + track.artist();
        }))).modifiers(FontSize.with(s -> s.size(13)), ForegroundStyle.with(f -> f.color(MusicPlayerView.SECONDARY_FG)));
    }
}