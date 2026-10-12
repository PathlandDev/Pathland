package com.pathland.demo.music;

import com.pathland.view.*;
import com.pathland.view.signal.WritableSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The library pane: the "Library" header and the full track list. Each row is a
 * {@link TrackRowView}; selecting one starts that track through the app-owned
 * player state (owned by the root view).
 *
 * <p>Configured with {@code LibraryView.make(l -> l.trackIndex(...).position(...)...)}
 * — a private constructor keeps creation on the configurator (spec DSL.md §2).
 */
public final class LibraryView implements View, Configurable<LibraryView.Config> {

    /** {@link LibraryView} values. */
    public static final class Config implements View.Config {

        private WritableSignal<Integer> trackIndex;
        private WritableSignal<Float> position;
        private WritableSignal<Boolean> playing;
        private WritableSignal<Float> seekRequest;

        /** The current track index. */
        public Config trackIndex(WritableSignal<Integer> trackIndex) {
            this.trackIndex = trackIndex;
            return this;
        }

        /** The play position (seconds), reset on selection. */
        public Config position(WritableSignal<Float> position) {
            this.position = position;
            return this;
        }

        /** Whether a track is playing. */
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

    private LibraryView(Config config) {
        this.config = config;
    }

    /** Configure the library pane's values. */
    public static ViewBuilder<LibraryView, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new LibraryView(config));
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public View body() {
        List<View> rows = new ArrayList<>();
        for (int i = 0; i < MusicPlayerView.TRACKS.size(); i++) {
            int index = i;
            rows.add(TrackRowView.make(r -> r
                    .track(MusicPlayerView.TRACKS.get(index))
                    .index(index)
                    .trackIndex(config.trackIndex)
                    .position(config.position)
                    .playing(config.playing)
                    .seekRequest(config.seekRequest)));
        }
        // A trailing spacer absorbs the leftover vertical space.
        rows.add(Spacer.make().modifiers());

        return VStack.make()
                .modifiers(
                        Padding.with(24),
                        Frame.with(f -> f.width(Commands.Size.FILL).height(Commands.Size.FILL)))
                .children(
                        Text.make("Library").modifiers(
                                FontSize.with(26), FontWeight.BOLD, AccessibilityRole.with(Roles.HEADER)),
                        Text.make(MusicPlayerView.TRACKS.size() + " songs · " + MusicPlayerView.TRACKS.size() + " albums")
                                .modifiers(ForegroundStyle.with(MusicPlayerView.SECONDARY_FG)),
Divider.make().modifiers(Padding.with(p -> p.edges(0, 0, 0, 10))),
                        ScrollView.children(VStack.children(rows.toArray(new View[0]))));
    }
}