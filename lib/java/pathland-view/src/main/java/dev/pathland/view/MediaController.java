package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;
import dev.pathland.view.signal.WritableSignal;

/**
 * A media playback controller — the app-side state object that owns a media
 * resource and its transport (the SwiftUI {@code AVPlayer} equivalent).
 * A {@code MEDIA_CONTROLLER} node is materialized in the retained tree by the
 * emitter whenever a view references this object (spec/MEDIA.md), so playback
 * identity is stable across structural swaps: a {@link Video} surface — or any
 * of the app's own control views — referencing the same {@code MediaController}
 * controls the same playback.
 *
 * <p><b>Audio has no view</b>: an audio playback is a {@code MediaController}
 * plus ordinary views (buttons/sliders) bound to its transport signals. There are
 * no renderer-native audio controls — the application defines the control surface.
 *
 * <p><b>Commands vs reports</b> (spec/EVENTS.md Media): the <em>commands</em>
 * ({@link #playing()}, {@link #position()}, {@link #volume()}, {@link #muted()},
 * {@link #loop()}, {@link #rate()}) are written by the app/user and emitted as
 * transport properties; the <em>reports</em> ({@link #time()},
 * {@link #duration()}, {@link #volumeReport()}) are fed by the renderer's
 * {@code MEDIA_*} events. Bind a control's <em>display</em> to the report and its
 * <em>drag</em> to the command so a report never echoes back as a command.
 *
 * <pre>{@code
 * MediaController player = new MediaController();
 * player.source("/_pathland/assets/audio/track1.mp3");
 * player.play();
 *
 * Button.make(b -> b.action(player::toggle))
 *     .children(Text.make(computed(() -> player.playing().get() ? "Pause" : "Play")));
 * }</pre>
 */
public final class MediaController {

    /** The resource: a static URL/asset, or a reactive binding (e.g. the current track). */
    private Signal<String> source = Signals.constant("");

    // Transport COMMANDS (the app/user writes these). Each may be rebound to an
    // app-owned (e.g. persisted) signal via the matching fluent setter.
    private WritableSignal<Boolean> playing = Signals.signal(false);
    private WritableSignal<Float> position = Signals.signal(0f);
    private WritableSignal<Float> volume = Signals.signal(1f);
    private WritableSignal<Boolean> muted = Signals.signal(false);
    private WritableSignal<Boolean> loop = Signals.signal(false);
    private WritableSignal<Float> rate = Signals.signal(1f);

    // Transport REPORTS (the renderer's MEDIA_* events flow into these).
    private WritableSignal<Float> time = Signals.signal(0f);
    private WritableSignal<Float> duration = Signals.signal(0f);
    private WritableSignal<Float> volumeReport = Signals.signal(1f);
    private Runnable onEnded = () -> {};

    /** A controller with no resource (idle until {@link #source} is set). */
    public MediaController() {}

    /** A controller for a static resource (resource name, file path, or URL). */
    public MediaController(String source) {
        source(source);
    }

    /** Set a static resource (resource name, file path, or URL). */
    public MediaController source(String source) {
        this.source = Signals.constant(source == null ? "" : source);
        return this;
    }

    /** Bind the resource to a reactive signal (e.g. the current track's audio). */
    public MediaController source(Signal<String> source) {
        this.source = source;
        return this;
    }

    /** Advance (e.g. to the next track) when the media ends ({@code MEDIA_ENDED}). */
    public MediaController onEnded(Runnable onEnded) {
        this.onEnded = onEnded == null ? () -> {} : onEnded;
        return this;
    }

    // ── Bind an app-owned signal to a transport slot ─────────────────────────
    // A controller normally owns its signals; an app that keeps its transport in
    // its own (e.g. persisted) signals can hand them over so the controller drives
    // the same state the rest of the UI reads.

    /** Use {@code signal} as the play/pause command. */
    public MediaController playing(WritableSignal<Boolean> signal) {
        this.playing = signal;
        return this;
    }

    /** Use {@code signal} as the seek command. */
    public MediaController position(WritableSignal<Float> signal) {
        this.position = signal;
        return this;
    }

    /** Use {@code signal} as the volume command. */
    public MediaController volume(WritableSignal<Float> signal) {
        this.volume = signal;
        return this;
    }

    /** Use {@code signal} as the mute command. */
    public MediaController muted(WritableSignal<Boolean> signal) {
        this.muted = signal;
        return this;
    }

    /** Use {@code signal} as the loop command. */
    public MediaController loop(WritableSignal<Boolean> signal) {
        this.loop = signal;
        return this;
    }

    /** Use {@code signal} as the rate command. */
    public MediaController rate(WritableSignal<Float> signal) {
        this.rate = signal;
        return this;
    }

    /** Use {@code signal} as the position report feed ({@code MEDIA_TIME_UPDATED}). */
    public MediaController time(WritableSignal<Float> signal) {
        this.time = signal;
        return this;
    }

    /** Use {@code signal} as the duration report feed ({@code MEDIA_DURATION_CHANGED}). */
    public MediaController duration(WritableSignal<Float> signal) {
        this.duration = signal;
        return this;
    }

    /** Use {@code signal} as the volume report feed ({@code MEDIA_VOLUME_CHANGED}). */
    public MediaController volumeReport(WritableSignal<Float> signal) {
        this.volumeReport = signal;
        return this;
    }

    // ── Convenience transport (thin wrappers over the command signals) ──────

    /** Start playback ({@code PLAYBACK_STATE} = 1). */
    public void play() {
        playing.set(true);
    }

    /** Pause playback ({@code PLAYBACK_STATE} = 0). */
    public void pause() {
        playing.set(false);
    }

    /** Toggle play/pause (a transport button's action). */
    public void toggle() {
        playing.update(v -> !v);
    }

    /** Seek to {@code seconds} (a change to {@code MEDIA_POSITION}). */
    public void seek(float seconds) {
        position.set(seconds);
    }

    /** Set the volume 0..1 (a change to {@code MEDIA_VOLUME}). */
    public void setVolume(float value) {
        volume.set(Math.max(0f, Math.min(1f, value)));
    }

    /** Mute/unmute (independent of {@link #volume()}). */
    public void setMuted(boolean value) {
        muted.set(value);
    }

    /** Loop the resource on end. */
    public void setLoop(boolean value) {
        loop.set(value);
    }

    /** Set the playback rate (1.0 = normal). */
    public void setRate(float value) {
        rate.set(value);
    }

    // ── Signals (read by the emitter; the app reads the reports) ────────────

    /** The bound resource signal. */
    public Signal<String> sourceSignal() {
        return source;
    }

    /** The play/pause command ({@code MEDIA_PLAY_STATE_CHANGED} echoes back into it). */
    public WritableSignal<Boolean> playing() {
        return playing;
    }

    /** The seek command ({@code MEDIA_POSITION}); display progress with {@link #time()}. */
    public WritableSignal<Float> position() {
        return position;
    }

    /** The volume command ({@code MEDIA_VOLUME}); display level with {@link #volumeReport()}. */
    public WritableSignal<Float> volume() {
        return volume;
    }

    /** The mute command. */
    public WritableSignal<Boolean> muted() {
        return muted;
    }

    /** The loop command. */
    public WritableSignal<Boolean> loop() {
        return loop;
    }

    /** The rate command. */
    public WritableSignal<Float> rate() {
        return rate;
    }

    /** The current playback position feed ({@code MEDIA_TIME_UPDATED}) — bind a display to it. */
    public WritableSignal<Float> time() {
        return time;
    }

    /** The media duration feed ({@code MEDIA_DURATION_CHANGED}) — a seek control's maximum. */
    public WritableSignal<Float> duration() {
        return duration;
    }

    /** The volume feed ({@code MEDIA_VOLUME_CHANGED}) — bind a display to it. */
    public WritableSignal<Float> volumeReport() {
        return volumeReport;
    }

    /** The end-of-media hook ({@code MEDIA_ENDED}). */
    public Runnable onEnded() {
        return onEnded;
    }

    /**
     * A **non-visual marker** that materializes this controller's
     * {@code MEDIA_CONTROLLER} node in the tree. A {@link Video} surface references
     * the controller automatically; a purely **audio** playback has no surface, so
     * place this marker once in the tree (it renders nothing and takes no space):
     *
     * <pre>{@code
     * VStack.make().children(player.attach(), playButton, seekBar, volumeSlider);
     * }</pre>
     */
    public View attach() {
        return new AttachMarker(this);
    }

    /** A non-visual node carrying the controller reference (spec/MEDIA.md): the
     *  emitter turns it into the controller's {@code MEDIA_CONTROLLER} node. */
    private static final class AttachMarker implements View {
        private final MediaController controller;

        AttachMarker(MediaController controller) {
            this.controller = controller;
        }

        @Override
        public PathlandNode render(Environment env) {
            PathlandNode node = new PathlandNode(Components.COMMENT);
            node.mediaControllerRef = controller;
            return node;
        }
    }
}
