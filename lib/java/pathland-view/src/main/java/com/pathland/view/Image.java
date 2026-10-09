package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/** An image leaf (a native element; the renderer resolves the visual). The
 *  source is a single {@link Signal<String>} — a change re-emits only this
 *  node's {@code IMAGE_SOURCE}; a static source is sugar for a constant signal. */
public final class Image implements View, Configurable<Image.Config> {

    /** {@link Image} values. */
    public static final class Config implements View.Config {

        private Signal<String> source;

        /** Set a static source (resource name, file path, or absolute URL). */
        public Config source(String source) {
            this.source = source == null ? null : Signals.constant(source);
            return this;
        }

        /** Bind the source to a reactive signal. */
        public Config source(Signal<String> source) {
            this.source = source;
            return this;
        }
    }

    private final Config config;

    private Image(Config config) {
        this.config = config;
    }

    /** An image with no source (the renderer's default visual). */
    public static Image of() {
        return new Image(new Config());
    }

    /** An image with a static source (resource name, file path, or absolute URL). */
    public static Image of(String source) {
        return new Image(new Config().source(source));
    }

    /** An image whose source is bound to a reactive signal. */
    public static Image of(Signal<String> source) {
        return new Image(new Config().source(source));
    }

    /** Configure the image's values. */
    public static ViewBuilder<Image, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Image(config));
    }

    /** Apply modifiers to a source-less image. */
    public static ViewBuilder<Image, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Image(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.IMAGE);
        if (config.source != null) {
            node.properties.put(Properties.IMAGE_SOURCE, config.source.get());
            node.propertyBindings.put(Properties.IMAGE_SOURCE, config.source);
        }
        return node;
    }
}
