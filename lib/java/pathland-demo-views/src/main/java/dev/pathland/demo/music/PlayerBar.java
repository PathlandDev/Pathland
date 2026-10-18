package dev.pathland.demo.music;

import dev.pathland.view.*;
import dev.pathland.view.signal.WritableSignal;
import static dev.pathland.view.Modifiers.*;

import java.util.function.Consumer;

/**
 * The bottom player bar: the {@link PlayerControls} surface over the player's
 * {@link MediaController} (spec/MEDIA.md). Audio has no view — this bar is an
 * ordinary view whose controls bind the controller's transport signals, so a
 * {@code SIZE_THAT_FITS} swap between this bar and the wide sidebar re-emits only
 * the controls; the controller's playback is untouched.
 *
 * <p>Configured with {@code PlayerBar.make(p -> p.trackIndex(...).player(...))} —
 * a private constructor keeps creation on the configurator (spec DSL.md §2).
 */
public final class PlayerBar implements View, Configurable<PlayerBar.Config> {

    /** {@link PlayerBar} values. */
    public static final class Config implements View.Config {

        private WritableSignal<Integer> trackIndex;
        private MediaController player;

        /** The current track index (drives the track info + prev/next). */
        public Config trackIndex(WritableSignal<Integer> trackIndex) {
            this.trackIndex = trackIndex;
            return this;
        }

        /** The playback controller the bar drives. */
        public Config player(MediaController player) {
            this.player = player;
            return this;
        }
    }

    private final Config config;

    private PlayerBar(Config config) {
        this.config = config;
    }

    /** Configure the player bar's values. */
    public static ViewBuilder<PlayerBar, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new PlayerBar(config));
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public View body() {
        return PlayerControls.make(c -> c
                        .player(config.player)
                        .trackIndex(config.trackIndex))
                .modifiers(
                        background(MusicPlayerView.BAR_BG),
                        frame(f -> f.width(Commands.Size.FILL).maxWidth(768).height(MusicPlayerView.BAR_HEIGHT)));
    }
}
