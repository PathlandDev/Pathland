package com.pathland.demo.music;

import com.pathland.view.*;
import com.pathland.view.signal.WritableSignal;

import static com.pathland.view.signal.Signals.computed;

/**
 * The music player's custom {@link AudioStyle}: the full bottom-bar surface —
 * a centered row of {@code [rewind] [play-pause] [forward] | [cover art]
 * [track title] | [volume]} over a slim, draggable seek bar that reports
 * <b>progress as a percent</b> (mapped in the UI model via {@link PercentSeek};
 * the wire keeps seconds). Everything binds to the media configuration's
 * signals, so this becomes the {@code AUDIO} node's children and the renderer
 * emits a hidden media element alongside these app-defined controls (real
 * playback, app-driven).
 */
public final class PlayerControlsStyle implements AudioStyle {

    private final WritableSignal<Integer> trackIndex;
    private final WritableSignal<Float> position;
    private final WritableSignal<Float> seekRequest;
    private final WritableSignal<Float> volume;
    private final WritableSignal<Float> volumeRequest;

    private PlayerControlsStyle(WritableSignal<Integer> trackIndex, WritableSignal<Float> position,
                                WritableSignal<Float> seekRequest, WritableSignal<Float> volume,
                                WritableSignal<Float> volumeRequest) {
        this.trackIndex = trackIndex;
        this.position = position;
        this.seekRequest = seekRequest;
        this.volume = volume;
        this.volumeRequest = volumeRequest;
    }

    /** A player-controls style: the seek/volume DISPLAY signals (fed by the media
     *  reports) and the SEEK/VOLUME command signals (written on user drags). */
    public static PlayerControlsStyle of(WritableSignal<Integer> trackIndex, WritableSignal<Float> position,
                                         WritableSignal<Float> seekRequest, WritableSignal<Float> volume,
                                         WritableSignal<Float> volumeRequest) {
        return new PlayerControlsStyle(trackIndex, position, seekRequest, volume, volumeRequest);
    }

    @Override
    public View makeBody(Configuration config) {
        var current = computed(() -> MusicPlayerView.at(trackIndex.get()));
        var transportIcon = computed(() -> config.playing().get() ? IconName.PAUSE.wire() : IconName.PLAY.wire());
        var seek = new SeekControl(position, seekRequest);
        var volumeControl = new VolumeControl(volume, volumeRequest);

        var playControl = HStack.with(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f)).children(
                Button.with(b -> b.action(() -> { prev(); restartSeek(); })).children(Icon.with(i -> i.name(IconName.SKIP_BACK))).modifiers(FontSize.with(s -> s.size(20))),
                Button.with(b -> b.action(() -> config.playing().update(v -> !v))).children(Icon.with(i -> i.name(transportIcon))).modifiers(FontSize.with(s -> s.size(28))),
                Button.with(b -> b.action(() -> { next(); restartSeek(); })).children(Icon.with(i -> i.name(IconName.SKIP_FORWARD))).modifiers(FontSize.with(s -> s.size(20)))
        );
        var trackInfo = HStack.with(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f)).children(
                Image.with(i -> i.source(computed(() -> current.get().cover())))
                        .modifiers(Frame.with(f -> f.width(36).height(36))).modifiers(ScaledToFit.with()),
                VStack.with(v -> v.alignment(HorizontalAlignment.LEADING).spacing(2)).children(
                        Text.with(t -> t.text(computed(() -> current.get().title())))
                                .modifiers(FontWeight.SEMIBOLD, LineLimit.with(l -> l.value(1))),
                        Text.with(t -> t.text(computed(() -> current.get().artist())))
                                .modifiers(FontSize.with(s -> s.size(13)), ForegroundStyle.with(f -> f.color(MusicPlayerView.SECONDARY_FG)), LineLimit.with(l -> l.value(1)))
                ).modifiers(Frame.ofWidth(160f))
        );

        var volumeView = HStack.with(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f)).children(
            Icon.with(i -> i.name(IconName.VOLUME)).modifiers(FontSize.with(s -> s.size(16))),
            Slider.with(s -> s.value(volumeControl).in(0f, 1f))
        );

        var compactControls = HStack.with(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f)).children(
                playControl,
                Text.with(t -> t.text("|")).modifiers(FontSize.with(s -> s.size(18)), ForegroundStyle.with(f -> f.color(MusicPlayerView.SECONDARY_FG))),
                trackInfo
        );

        var controls = HStack.with(h -> h.alignment(VerticalAlignment.CENTER).spacing(18f)).children(
                playControl,
                Text.with(t -> t.text("|")).modifiers(FontSize.with(s -> s.size(18)), ForegroundStyle.with(f -> f.color(MusicPlayerView.SECONDARY_FG))),
                trackInfo,
                Text.with(t -> t.text("|")).modifiers(FontSize.with(s -> s.size(18)), ForegroundStyle.with(f -> f.color(MusicPlayerView.SECONDARY_FG))),
                volumeView
        );

        var groups = SizeThatFits.of(
                Fit.of(controls, 600),
                Fit.of(compactControls)
        );

        return VStack.with(v -> v.alignment(HorizontalAlignment.CENTER).spacing(0)).children(
                // Centered groups over a full-width seek bar (the slider's FILL
                // width spans the bar via fill propagation; the groups stay
                // centered above it).
                groups,
                Slider.with(s -> s.value(new PercentSeek(seek,
                                () -> MusicPlayerView.at(trackIndex.get()).duration()))
                        .in(0f, 100f).onEditingChanged(seek::onEditingChanged))
                        .modifiers(Frame.ofWidth(Float.POSITIVE_INFINITY)).modifiers(Padding.with(p -> p.edges(0, 16, 0, 16)))
        );
    }

    /** Previous track (wraps around); the seek restarts via the command binding. */
    private void prev() {
        trackIndex.update(i -> (i - 1 + MusicPlayerView.TRACKS.size()) % MusicPlayerView.TRACKS.size());
    }

    /** Next track (wraps around); the seek restarts via the command binding. */
    private void next() {
        trackIndex.update(i -> (i + 1) % MusicPlayerView.TRACKS.size());
    }

    /** A prev/next restart seeks to the top: reset both the display and the command. */
    private void restartSeek() {
        position.set(0f);
        seekRequest.set(0f);
    }
}