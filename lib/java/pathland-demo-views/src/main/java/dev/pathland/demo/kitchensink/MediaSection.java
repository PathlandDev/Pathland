package dev.pathland.demo.kitchensink;

import dev.pathland.view.Alignment;
import dev.pathland.view.Button;
import dev.pathland.view.Configurable;
import dev.pathland.view.Frame;
import dev.pathland.view.HStack;
import dev.pathland.view.Icon;
import dev.pathland.view.IconName;
import dev.pathland.view.MediaController;
import dev.pathland.view.Padding;
import dev.pathland.view.Text;
import dev.pathland.view.VerticalAlignment;
import dev.pathland.view.VStack;
import dev.pathland.view.Video;
import dev.pathland.view.View;
import dev.pathland.view.ViewBuilder;
import java.util.function.Consumer;
import static dev.pathland.view.Modifiers.*;

/**
 * Media section: a {@link MediaController}'s {@link Video} surface plus an
 * **app-supplied** transport control (spec/MEDIA.md). The controller is passed in
 * from the root — created **once**, outside any swapping branch — so its
 * {@code MEDIA_CONTROLLER} node (materialized by {@code attach()}) keeps a stable
 * node id and playback survives a structural swap.
 *
 * <p>The surface renders renderer-native video controls; the play/pause button
 * next to it proves the transport is the app's own (the same controller drives
 * both). Audio has no view at all — an audio playback is a controller plus the
 * app's views, exactly like this button. Configured with
 * {@code MediaSection.make(m -> m.player(controller))} (spec DSL.md §2).
 */
public final class MediaSection implements View, Configurable<MediaSection.Config> {

    /** {@link MediaSection} values. */
    public static final class Config implements View.Config {
        private MediaController player;

        /** The playback controller whose surface this section shows. */
        public Config player(MediaController player) {
            this.player = player;
            return this;
        }
    }

    private final Config config;

    private MediaSection(Config config) {
        this.config = config;
    }

    /** Configure the section's values. */
    public static ViewBuilder<MediaSection, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new MediaSection(config));
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public View body() {
        MediaController player = config.player;
        View content = VStack.make()
                .modifiers(padding(4))
                .children(
                        // Materializes the controller's non-visual node in place.
                        player.attach(),
                        Video.make(player)
                                .modifiers(frame(f -> f.width(320).height(180).alignment(Alignment.CENTER))),
                        HStack.make(h -> h.alignment(VerticalAlignment.CENTER).spacing(8f))
                                .children(
                                        Button.make(b -> b.action(player::toggle))
                                                .children(Icon.make(player.playing().get()
                                                        ? IconName.PAUSE : IconName.PLAY)),
                                        Text.make("A MEDIA_CONTROLLER's surface — the transport is the app's own.")));
        return SectionCard.make("Media · video (a MediaController's surface)").children(content);
    }
}
