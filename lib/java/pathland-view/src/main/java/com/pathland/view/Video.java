package com.pathland.view;

import com.pathland.view.emit.MediaInput;
import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

/**
 * A video playback node. Playback is **renderer-native by default** (the web
 * renderer emits {@code <video controls>}); a custom {@link VideoStyle} supplies
 * app-defined control children, mirroring {@link Audio}. A poster/preview frame
 * is a planned draft ({@code POSTER_SOURCE}).
 */
public final class Video implements View, Configurable<Video.Config> {

    /** {@link Video} values. */
    public static final class Config implements View.Config {

        private Signal<String> source;
        private WritableSignal<Boolean> playing;
        private WritableSignal<Float> position;
        private WritableSignal<Float> volume;
        private float duration;
        private Runnable onEnded;

        /** Set a static source (resource name, file path, or URL). */
        public Config source(String source) {
            this.source = source == null ? null : Signals.constant(source);
            return this;
        }

        /** Bind the source to a reactive signal. */
        public Config source(Signal<String> source) {
            this.source = source;
            return this;
        }

        /** Bind the play/pause state (a {@code MEDIA_PLAY_STATE_CHANGED} echoes back into it). */
        public Config playing(WritableSignal<Boolean> playing) {
            this.playing = playing;
            return this;
        }

        /** Bind the play position in seconds (a change seeks; {@code MEDIA_TIME_UPDATED} echoes back). */
        public Config position(WritableSignal<Float> position) {
            this.position = position;
            return this;
        }

        /** Bind the volume 0..1 ({@code MEDIA_VOLUME_CHANGED} echoes back). */
        public Config volume(WritableSignal<Float> volume) {
            this.volume = volume;
            return this;
        }

        /** The media length in seconds (a custom seek control's maximum). */
        public Config duration(float seconds) {
            this.duration = seconds;
            return this;
        }

        /** Advance when the media ends ({@code MEDIA_ENDED}). */
        public Config onEnded(Runnable onEnded) {
            this.onEnded = onEnded;
            return this;
        }
    }

    private final Config config;

    private Video(Config config) {
        this.config = config;
    }

    /** A video node with no source (the renderer's default visual). */
    public static Video of() {
        return new Video(new Config());
    }

    /** A video node with a static source (resource name, file path, or URL). */
    public static Video of(String source) {
        return new Video(new Config().source(source));
    }

    /** A video node whose source is bound to a reactive signal. */
    public static Video of(Signal<String> source) {
        return new Video(new Config().source(source));
    }

    /** Configure the video node's values. */
    public static ViewBuilder<Video, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Video(config));
    }

    /** Apply modifiers to a source-less video node. */
    public static ViewBuilder<Video, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Video(new Config())).modifiers(modifiers);
    }

    /** Bind the play/pause state (a {@code MEDIA_PLAY_STATE_CHANGED} echoes back into it). */
    public Video playing(WritableSignal<Boolean> playing) {
        config.playing(playing);
        return this;
    }

    /** Bind the play position in seconds (a change seeks; {@code MEDIA_TIME_UPDATED} echoes back). */
    public Video position(WritableSignal<Float> position) {
        config.position(position);
        return this;
    }

    /** Bind the volume 0..1 ({@code MEDIA_VOLUME_CHANGED} echoes back). */
    public Video volume(WritableSignal<Float> volume) {
        config.volume(volume);
        return this;
    }

    /** The media length in seconds (a custom seek control's maximum). */
    public Video duration(float seconds) {
        config.duration(seconds);
        return this;
    }

    /** Advance when the media ends ({@code MEDIA_ENDED}). */
    public Video onEnded(Runnable onEnded) {
        config.onEnded(onEnded);
        return this;
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        // The control owns the native VIDEO node; the active style supplies its
        // content as the node's child (control-owned interaction, spec DSL.md §5.7).
        // The default NativeVideoStyle supplies no content, so the node stays a leaf
        // and the renderer shows native media controls.
        PathlandNode node = new PathlandNode(Components.VIDEO);
        VideoStyle style = env.videoStyle();
        View content = style.makeBody(
                new VideoStyle.Configuration(config.playing, config.position, config.volume, config.duration));
        if (content != null && content != EmptyContent.INSTANCE) {
            node.children.add(content.render(env));
        }

        if (config.source != null) {
            node.properties.put(Properties.VIDEO_SOURCE, config.source.get());
            node.propertyBindings.put(Properties.VIDEO_SOURCE, config.source);
        }
        if (config.playing != null) {
            node.properties.put(Properties.PLAYBACK_STATE, config.playing.get() ? 1 : 0);
            node.propertyBindings.put(Properties.PLAYBACK_STATE, config.playing);
        }
        if (config.position != null) {
            node.properties.put(Properties.MEDIA_POSITION, config.position.get());
            node.propertyBindings.put(Properties.MEDIA_POSITION, config.position);
        }
        if (config.volume != null) {
            node.properties.put(Properties.MEDIA_VOLUME, config.volume.get());
            node.propertyBindings.put(Properties.MEDIA_VOLUME, config.volume);
        }
        if (config.playing != null || config.position != null || config.volume != null || config.onEnded != null) {
            node.mediaInput = new MediaInput() {
                @Override
                public void onPlayStateChanged(boolean value) {
                    if (config.playing != null) {
                        config.playing.set(value);
                    }
                }

                @Override
                public void onTimeUpdated(float seconds) {
                    if (config.position != null) {
                        config.position.set(seconds);
                    }
                }

                @Override
                public void onEnded() {
                    if (config.onEnded != null) {
                        config.onEnded.run();
                    }
                }

                @Override
                public void onVolumeChanged(float value) {
                    if (config.volume != null) {
                        config.volume.set(value);
                    }
                }
            };
        }
        return node;
    }
}
