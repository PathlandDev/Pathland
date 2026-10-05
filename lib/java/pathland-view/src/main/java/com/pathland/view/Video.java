package com.pathland.view;

import com.pathland.view.emit.MediaInput;
import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;

/**
 * A video playback node. Playback is **renderer-native by default** (the web
 * renderer emits {@code <video controls>}); a custom {@link VideoStyle} supplies
 * app-defined control children, mirroring {@link Audio}. A poster/preview frame
 * is a planned draft ({@code POSTER_SOURCE}).
 */
public final class Video implements View {

    private final Signal<String> sourceSignal;
    private final WritableSignal<Boolean> playing;
    private final WritableSignal<Float> position;
    private final WritableSignal<Float> volume;
    private final float duration;
    private final Runnable onEnded;

    private Video(Signal<String> sourceSignal, WritableSignal<Boolean> playing,
                  WritableSignal<Float> position, WritableSignal<Float> volume,
                  float duration, Runnable onEnded) {
        this.sourceSignal = sourceSignal;
        this.playing = playing;
        this.position = position;
        this.volume = volume;
        this.duration = duration;
        this.onEnded = onEnded;
    }

    /** A video node with no source (the renderer's default visual). */
    public static Video of() {
        return new Video(null, null, null, null, 0f, null);
    }

    /** A video node with a static source (resource name, file path, or URL). */
    public static Video of(String source) {
        return new Video(Signals.constant(source), null, null, null, 0f, null);
    }

    /** A video node whose source is bound to a reactive signal. */
    public static Video of(Signal<String> source) {
        return new Video(source, null, null, null, 0f, null);
    }

    /** Bind the play/pause state (a {@code MEDIA_PLAY_STATE_CHANGED} echoes back into it). */
    public Video playing(WritableSignal<Boolean> playing) {
        return new Video(sourceSignal, playing, position, volume, duration, onEnded);
    }

    /** Bind the play position in seconds (a change seeks; {@code MEDIA_TIME_UPDATED} echoes back). */
    public Video position(WritableSignal<Float> position) {
        return new Video(sourceSignal, playing, position, volume, duration, onEnded);
    }

    /** Bind the volume 0..1 ({@code MEDIA_VOLUME_CHANGED} echoes back). */
    public Video volume(WritableSignal<Float> volume) {
        return new Video(sourceSignal, playing, position, volume, duration, onEnded);
    }

    /** The media length in seconds (a custom seek control's maximum). */
    public Video duration(float seconds) {
        return new Video(sourceSignal, playing, position, volume, seconds, onEnded);
    }

    /** Advance when the media ends ({@code MEDIA_ENDED}). */
    public Video onEnded(Runnable onEnded) {
        return new Video(sourceSignal, playing, position, volume, duration, onEnded);
    }

    @Override
    public PathlandNode render(Environment env) {
        // The control owns the native VIDEO node; the active style supplies its
        // content as the node's child (control-owned interaction, spec DSL.md §5.7).
        // The default NativeVideoStyle supplies no content, so the node stays a leaf
        // and the renderer shows native media controls.
        PathlandNode node = new PathlandNode(Components.VIDEO);
        VideoStyle style = env.videoStyle();
        View content = style.makeBody(new VideoStyle.Configuration(playing, position, volume, duration));
        if (content != null && content != EmptyContent.INSTANCE) {
            node.children.add(content.render(env));
        }

        if (sourceSignal != null) {
            node.properties.put(Properties.VIDEO_SOURCE, sourceSignal.get());
            node.propertyBindings.put(Properties.VIDEO_SOURCE, sourceSignal);
        }
        if (playing != null) {
            node.properties.put(Properties.PLAYBACK_STATE, playing.get() ? 1 : 0);
            node.propertyBindings.put(Properties.PLAYBACK_STATE, playing);
        }
        if (position != null) {
            node.properties.put(Properties.MEDIA_POSITION, position.get());
            node.propertyBindings.put(Properties.MEDIA_POSITION, position);
        }
        if (volume != null) {
            node.properties.put(Properties.MEDIA_VOLUME, volume.get());
            node.propertyBindings.put(Properties.MEDIA_VOLUME, volume);
        }
        if (playing != null || position != null || volume != null || onEnded != null) {
            node.mediaInput = new MediaInput() {
                @Override
                public void onPlayStateChanged(boolean value) {
                    if (playing != null) {
                        playing.set(value);
                    }
                }

                @Override
                public void onTimeUpdated(float seconds) {
                    if (position != null) {
                        position.set(seconds);
                    }
                }

                @Override
                public void onEnded() {
                    if (Video.this.onEnded != null) {
                        Video.this.onEnded.run();
                    }
                }

                @Override
                public void onVolumeChanged(float value) {
                    if (volume != null) {
                        volume.set(value);
                    }
                }
            };
        }
        return node;
    }
}