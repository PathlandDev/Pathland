package com.pathland.view;

import com.pathland.view.emit.MediaInput;
import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;

/**
 * An audio playback node. Playback is **renderer-native by default** (the web
 * renderer emits {@code <audio controls>}); a custom {@link AudioStyle} supplies
 * app-defined control children — the node then carries the media control
 * properties ({@code PLAYBACK_STATE}/{@code MEDIA_POSITION}/{@code MEDIA_VOLUME})
 * bound to the playback signals, and the renderer reports playback events back
 * into them (spec/EVENTS.md Media).
 *
 * <p>Bind a control signal with {@link #playing(WritableSignal)},
 * {@link #position(WritableSignal)}, {@link #volume(WritableSignal)}; the seek
 * slider's maximum is {@link #duration(float)}. A media node with bound controls
 * and a custom style is fully app-driven.
 */
public final class Audio implements View {

    private final Signal<String> sourceSignal;
    private final WritableSignal<Boolean> playing;
    private final WritableSignal<Float> position;
    private final WritableSignal<Float> volume;
    private final WritableSignal<Float> reportPosition;
    private final WritableSignal<Float> reportVolume;
    private final float duration;
    private final Runnable onEnded;

    private Audio(Signal<String> sourceSignal, WritableSignal<Boolean> playing,
                  WritableSignal<Float> position, WritableSignal<Float> volume,
                  WritableSignal<Float> reportPosition, WritableSignal<Float> reportVolume,
                  float duration, Runnable onEnded) {
        this.sourceSignal = sourceSignal;
        this.playing = playing;
        this.position = position;
        this.volume = volume;
        this.reportPosition = reportPosition;
        this.reportVolume = reportVolume;
        this.duration = duration;
        this.onEnded = onEnded;
    }

    /** An audio node with no source (the renderer's default visual). */
    public static Audio of() {
        return new Audio(null, null, null, null, null, null, 0f, null);
    }

    /** An audio node with a static source (resource name, file path, or URL). */
    public static Audio of(String source) {
        return new Audio(Signals.constant(source), null, null, null, null, null, 0f, null);
    }

    /** An audio node whose source is bound to a reactive signal. */
    public static Audio of(Signal<String> source) {
        return new Audio(source, null, null, null, null, null, 0f, null);
    }

    /** Bind the play/pause state (a {@code MEDIA_PLAY_STATE_CHANGED} echoes back into it). */
    public Audio playing(WritableSignal<Boolean> playing) {
        return new Audio(sourceSignal, playing, position, volume, reportPosition, reportVolume, duration, onEnded);
    }

    /** Bind the play position in seconds (a change seeks; {@code MEDIA_TIME_UPDATED} echoes back into it
     *  unless {@link #onPositionReport} redirects the report). */
    public Audio position(WritableSignal<Float> position) {
        return new Audio(sourceSignal, playing, position, volume, reportPosition, reportVolume, duration, onEnded);
    }

    /** Bind the volume 0..1 ({@code MEDIA_VOLUME_CHANGED} echoes back into it unless {@link #onVolumeReport}). */
    public Audio volume(WritableSignal<Float> volume) {
        return new Audio(sourceSignal, playing, position, volume, reportPosition, reportVolume, duration, onEnded);
    }

    /** Where {@code MEDIA_TIME_UPDATED} position reports flow (the seek-bar
     *  display), decoupled from the {@link #position seek command} — so a report
     *  never echoes back as a {@code MEDIA_POSITION} seek (spec/EVENTS.md Media).
     *  Defaults to the {@link #position} signal when unset. */
    public Audio onPositionReport(WritableSignal<Float> display) {
        return new Audio(sourceSignal, playing, position, volume, display, reportVolume, duration, onEnded);
    }

    /** Where {@code MEDIA_VOLUME_CHANGED} volume reports flow (the volume-slider
     *  display), decoupled from the {@link #volume command}. Defaults to the
     *  {@link #volume} signal when unset. */
    public Audio onVolumeReport(WritableSignal<Float> display) {
        return new Audio(sourceSignal, playing, position, volume, reportPosition, display, duration, onEnded);
    }

    /** The media length in seconds (a custom seek control's maximum). */
    public Audio duration(float seconds) {
        return new Audio(sourceSignal, playing, position, volume, reportPosition, reportVolume, seconds, onEnded);
    }

    /** Advance (e.g. to the next track) when the media ends ({@code MEDIA_ENDED}). */
    public Audio onEnded(Runnable onEnded) {
        return new Audio(sourceSignal, playing, position, volume, reportPosition, reportVolume, duration, onEnded);
    }

    @Override
    public PathlandNode render(Environment env) {
        // The control owns the native AUDIO node; the active style supplies its
        // content as the node's child (control-owned interaction, spec DSL.md §5.7).
        // The default NativeAudioStyle supplies no content, so the node stays a leaf
        // and the renderer shows native media controls.
        PathlandNode node = new PathlandNode(Components.AUDIO);
        AudioStyle style = env.audioStyle();
        View content = style.makeBody(new AudioStyle.Configuration(playing, position, volume, duration));
        if (content != null && content != EmptyContent.INSTANCE) {
            node.children.add(content.render(env));
        }

        if (sourceSignal != null) {
            node.properties.put(Properties.AUDIO_SOURCE, sourceSignal.get());
            node.propertyBindings.put(Properties.AUDIO_SOURCE, sourceSignal);
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
                    // Position REPORTS flow to the display (reportPosition when
                    // decoupled, else the position signal) — never echoed back as
                    // a MEDIA_POSITION seek command (spec/EVENTS.md Media).
                    WritableSignal<Float> sink = reportPosition != null ? reportPosition : position;
                    if (sink != null) {
                        sink.set(seconds);
                    }
                }

                @Override
                public void onEnded() {
                    if (Audio.this.onEnded != null) {
                        Audio.this.onEnded.run();
                    }
                }

                @Override
                public void onVolumeChanged(float value) {
                    WritableSignal<Float> sink = reportVolume != null ? reportVolume : volume;
                    if (sink != null) {
                        sink.set(value);
                    }
                }
            };
        }
        return node;
    }
}