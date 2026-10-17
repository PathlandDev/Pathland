package dev.pathland.demo.music;

import dev.pathland.view.*;
import dev.pathland.view.signal.WritableSignal;
import static dev.pathland.view.Modifiers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static dev.pathland.view.signal.Signals.computed;

/**
 * The now-playing sidebar (right): the current track's large album art, its
 * title/artist, and the next two tracks. Everything is bound to the current
 * {@code trackIndex}, so a track change re-emits only this pane's deltas.
 *
 * <p>An optional {@code adUrl} appends a **sponsored** slot — a small
 * {@link ProxyView} embedding a second Pathland app (spec/PROXY.md).
 *
 * <p>Configured with {@code NowPlayingSidebar.make(s -> s.trackIndex(...))} — a
 * private constructor keeps creation on the configurator (spec DSL.md §2).
 */
public final class NowPlayingSidebar implements View, Configurable<NowPlayingSidebar.Config> {

    /** {@link NowPlayingSidebar} values. */
    public static final class Config implements View.Config {

        private WritableSignal<Integer> trackIndex;
        private String adUrl;

        /** The current track index (drives the art + up-next). */
        public Config trackIndex(WritableSignal<Integer> trackIndex) {
            this.trackIndex = trackIndex;
            return this;
        }

        /** A second Pathland app's base URL to embed as a sponsored slot (spec/PROXY.md). */
        public Config adUrl(String adUrl) {
            this.adUrl = adUrl;
            return this;
        }
    }

    private final Config config;

    private NowPlayingSidebar(Config config) {
        this.config = config;
    }

    /** Configure the sidebar's values. */
    public static ViewBuilder<NowPlayingSidebar, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new NowPlayingSidebar(config));
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public View body() {
        List<View> children = new ArrayList<>(List.of(
                Text.make("Now Playing").modifiers(
                        fontSize(18), FontWeight.BOLD, accessibilityRole(Roles.HEADER)),
                Image.make(computed(() -> MusicPlayerView.at(config.trackIndex.get()).cover())).modifiers(
                        frame(f -> f.width(220).height(220)),
                        scaledToFill(),
                        border(b -> b.color(Color.BLACK).width(1).radius(8))),
                Text.make(computed(() -> MusicPlayerView.at(config.trackIndex.get()).title()))
                        .modifiers(fontSize(20), FontWeight.BOLD),
                Text.make(computed(() -> MusicPlayerView.at(config.trackIndex.get()).artist()
                        + " · " + MusicPlayerView.at(config.trackIndex.get()).album()))
                        .modifiers(foregroundStyle(MusicPlayerView.SECONDARY_FG)),
                Divider.make().modifiers(padding(p -> p.edges(0, 0, 0, 10))),
                Text.make("Up Next").modifiers(FontWeight.SEMIBOLD),
                upNextRow(1),
                upNextRow(2)));
        if (config.adUrl != null) {
            children.add(adCard(config.adUrl));
        }
        return VStack.make(v -> v.alignment(HorizontalAlignment.LEADING).spacing(14))
                .modifiers(
                        padding(24),
                        background(MusicPlayerView.SIDEBAR_BG),
                        frame(f -> f.width(MusicPlayerView.SIDEBAR_WIDTH).height(Commands.Size.FILL)))
                .children(children.toArray(new View[0]));
    }

    /**
     * The sponsored slot: a small card embedding a **second Pathland app** via a
     * {@link ProxyView} (spec/PROXY.md). The proxying server remaps the origin
     * app's node ids into a disjoint range and routes its events back, so the
     * embedded app is fully interactive in place.
     */
    private static View adCard(String adUrl) {
        return VStack.make(v -> v.alignment(HorizontalAlignment.LEADING).spacing(6))
                .modifiers(
                        padding(12),
                        cornerRadius(10),
                        border(b -> b.color(MusicPlayerView.SECONDARY_FG).width(1).radius(10)))
                .children(
                        Text.make("Sponsored").modifiers(
                                fontSize(11), FontWeight.SEMIBOLD,
                                foregroundStyle(MusicPlayerView.SECONDARY_FG)),
                        ProxyView.make(p -> p.endpoint(adUrl).route("/"))
                                .modifiers(frame(f -> f.width(220).height(140))));
    }

    /** The track {@code offset} places after the current one. */
    private View upNextRow(int offset) {
        return Text.make(computed(() -> {
            Track track = MusicPlayerView.at(config.trackIndex.get() + offset);
            return (offset == 1 ? "Up next · " : "Then · ") + track.title() + " — " + track.artist();
        })).modifiers(fontSize(13), foregroundStyle(MusicPlayerView.SECONDARY_FG));
    }
}