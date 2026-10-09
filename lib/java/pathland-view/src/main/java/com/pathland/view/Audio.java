package com.pathland.view;

import com.pathland.view.emit.MediaInput;
import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

/**
 * An audio playback node. Playback is **renderer-native by default** (the web
 * renderer emits {@code <audio controls>}); a custom {@link AudioStyle} supplies
 * app-defined control children — the node then carries the media control
 * properties ({@code PLAYBACK_STATE}/{@code MEDIA_POSITION}/{@code MEDIA_VOLUME})
 * bound to the playback signals, and the renderer reports playback events back
 * into them (spec/EVENTS.md Media).
 */
public final class Audio implements View, Configurable<Audio.Config> {

    /** {@link Audio} values. */
    public static final class Config implements View.Config {

        private Signal<String> source;
        private WritableSignal<Boolean> playing;
        private WritableSignal<Float> position;
        private WritableSignal<Float> volume;
        private WritableSignal<Float> reportPosition;
        private WritableSignal<Float> reportVolume;
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

        /** Bind the play position in seconds (a change seeks; {@code MEDIA_TIME_UPDATED} echoes back
         *  unless {@link #onPositionReport} redirects the report). */
        public Config position(WritableSignal<Float> position) {
            this.position = position;
            return this;
        }

        /** Bind the volume 0..1 ({@code MEDIA_VOLUME_CHANGED} echoes back unless {@link #onVolumeReport}). */
        public Config volume(WritableSignal<Float> volume) {
            this.volume = volume;
            return this;
        }

        /** Where {@code MEDIA_TIME_UPDATED} position reports flow (decoupled from the seek command). */
        public Config onPositionReport(WritableSignal<Float> display) {
            this.reportPosition = display;
            return this;
        }

        /** Where {@code MEDIA_VOLUME_CHANGED} volume reports flow (decoupled from the volume command). */
        public Config onVolumeReport(WritableSignal<Float> display) {
            this.reportVolume = display;
            return this;
        }

        /** The media length in seconds (a custom seek control's maximum). */
        public Config duration(float seconds) {
            this.duration = seconds;
            return this;
        }

        /** Advance (e.g. to the next track) when the media ends ({@code MEDIA_ENDED}). */
        public Config onEnded(Runnable onEnded) {
            this.onEnded = onEnded;
            return this;
        }
    }

    private final Config config;

    private Audio(Config config) {
        this.config = config;
    }

    /** An audio node with no source (the renderer's default visual). */
    public static Audio of() {
        return new Audio(new Config());
    }

    /** An audio node with a static source (resource name, file path, or URL). */
    public static Audio of(String source) {
        return new Audio(new Config().source(source));
    }

    /** An audio node whose source is bound to a reactive signal. */
    public static Audio of(Signal<String> source) {
        return new Audio(new Config().source(source));
    }

    /** Configure the audio node's values. */
    public static ViewBuilder<Audio, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Audio(config));
    }

    /** Apply modifiers to a source-less audio node. */
    public static ViewBuilder<Audio, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Audio(new Config())).modifiers(modifiers);
    }

    /** Bind the play/pause state (a {@code MEDIA_PLAY_STATE_CHANGED} echoes back into it). */
    public Audio playing(WritableSignal<Boolean> playing) {
        config.playing(playing);
        return this;
    }

    /** Bind the play position in seconds (a change seeks; {@code MEDIA_TIME_UPDATED} echoes back into it
     *  unless {@link #onPositionReport} redirects the report). */
    public Audio position(WritableSignal<Float> position) {
        config.position(position);
        return this;
    }

    /** Bind the volume 0..1 ({@code MEDIA_VOLUME_CHANGED} echoes back into it unless {@link #onVolumeReport}). */
    public Audio volume(WritableSignal<Float> volume) {
        config.volume(volume);
        return this;
    }

    /** Where {@code MEDIA_TIME_UPDATED} position reports flow (the seek-bar display). */
    public Audio onPositionReport(WritableSignal<Float> display) {
        config.onPositionReport(display);
        return this;
    }

    /** Where {@code MEDIA_VOLUME_CHANGED} volume reports flow (the volume-slider display). */
    public Audio onVolumeReport(WritableSignal<Float> display) {
        config.onVolumeReport(display);
        return this;
    }

    /** The media length in seconds (a custom seek control's maximum). */
    public Audio duration(float seconds) {
        config.duration(seconds);
        return this;
    }

    /** Advance (e.g. to the next track) when the media ends ({@code MEDIA_ENDED}). */
    public Audio onEnded(Runnable onEnded) {
        config.onEnded(onEnded);
        return this;
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        // The control owns the native AUDIO node; the active style supplies its
        // content as the node's child (control-owned interaction, spec DSL.md §5.7).
        // The default NativeAudioStyle supplies no content, so the node stays a leaf
        // and the renderer shows native media controls.
        PathlandNode node = new PathlandNode(Components.AUDIO);
        AudioStyle style = env.audioStyle();
        View content = style.makeBody(
                new AudioStyle.Configuration(config.playing, config.position, config.volume, config.duration));
        if (content != null && content != EmptyContent.INSTANCE) {
            node.children.add(content.render(env));
        }

        if (config.source != null) {
            node.properties.put(Properties.AUDIO_SOURCE, config.source.get());
            node.propertyBindings.put(Properties.AUDIO_SOURCE, config.source);
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
                    // Position REPORTS flow to the display (reportPosition when
                    // decoupled, else the position signal) — never echoed back as
                    // a MEDIA_POSITION seek command (spec/EVENTS.md Media).
                    WritableSignal<Float> sink =
                            config.reportPosition != null ? config.reportPosition : config.position;
                    if (sink != null) {
                        sink.set(seconds);
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
                    WritableSignal<Float> sink =
                            config.reportVolume != null ? config.reportVolume : config.volume;
                    if (sink != null) {
                        sink.set(value);
                    }
                }
            };
        }
        return node;
    }
}
