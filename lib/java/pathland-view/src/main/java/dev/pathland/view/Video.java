package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;

import java.util.function.Consumer;

/**
 * A video playback **surface**: a view that renders a {@link MediaController}'s
 * video output in its own box (spec/MEDIA.md). It references the controller —
 * the relationship is a reference, not a parent/child edge — so playback survives
 * structural swaps (a {@code SIZE_THAT_FITS} band change re-emits only this
 * surface; the controller's playback is untouched).
 *
 * <p>Playback is **renderer-native by default** (the web renderer emits
 * {@code <video controls>}); a custom {@link VideoStyle} supplies app-defined
 * control children. A poster/preview frame is a planned draft
 * ({@code POSTER_SOURCE}).
 */
public final class Video implements View, Configurable<Video.Config> {

    /** {@link Video} values. */
    public static final class Config implements View.Config {

        private MediaController controller;

        /** The controller whose video output this surface renders (spec/MEDIA.md). */
        public Config controller(MediaController controller) {
            this.controller = controller;
            return this;
        }
    }

    private final Config config;

    private Video(Config config) {
        this.config = config;
    }

    /** A video surface with no controller (renders blank/idle). */
    public static ViewBuilder<Video, Config> make() {
        return new ViewBuilder<>(new Video(new Config()));
    }

    /** A video surface rendering {@code controller}'s video output. */
    public static ViewBuilder<Video, Config> make(MediaController controller) {
        return Configurators.view(Config::new, Video::new, c -> c.controller(controller));
    }

    /** Configure the video surface's values. */
    public static ViewBuilder<Video, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Video(config));
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        // The surface owns the native VIDEO node; the active style supplies its
        // content as the node's child (control-owned interaction, spec DSL.md §5.7).
        // The default NativeVideoStyle supplies no content, so the node stays a leaf
        // and the renderer shows native media controls.
        PathlandNode node = new PathlandNode(Components.VIDEO);
        VideoStyle style = env.videoStyle();
        View content = style.makeBody(new VideoStyle.Configuration(config.controller));
        if (content != null && content != EmptyContent.INSTANCE) {
            node.children.add(content.render(env));
        }
        // The emitter materializes the controller's MEDIA_CONTROLLER node (by
        // object identity) and writes its id as MEDIA_CONTROLLER_ID here.
        node.mediaControllerRef = config.controller;
        return node;
    }
}
