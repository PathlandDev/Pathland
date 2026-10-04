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
        var glyph = computed(() -> config.playing().get() ? "⏸" : "▶");
        // The sliders PRESENT the display signals but WRITE the commands on a
        // drag (decoupling a report from a command, spec/EVENTS.md Media).
        var seek = new SeekControl(position, seekRequest);
        var volumeControl = new VolumeControl(volume, volumeRequest);
        var groups = HStack.of(VerticalAlignment.CENTER, 18f,
                Button.of("⏮", () -> { prev(); restartSeek(); }).with(FontSize.of(20)),
                Button.of(Text.of(glyph), () -> config.playing().update(v -> !v)).with(FontSize.of(28)),
                Button.of("⏭", () -> { next(); restartSeek(); }).with(FontSize.of(20)),
                Text.of("|").with(FontSize.of(18), ForegroundStyle.of(MusicPlayerView.SECONDARY_FG)),
                Image.of(computed(() -> current.get().cover()))
                        .with(FrameMod.of(36, 36), ScaledToFit.of()),
                VStack.of(HorizontalAlignment.LEADING, 2,
                        Text.of(computed(() -> current.get().title()))
                                .with(FontWeightMod.of(FontWeight.SEMIBOLD), LineLimit.of(1)),
                        Text.of(computed(() -> current.get().artist()))
                                .with(FontSize.of(13), ForegroundStyle.of(MusicPlayerView.SECONDARY_FG), LineLimit.of(1))
                ).with(FrameMod.of(160f)),
                Text.of("|").with(FontSize.of(18), ForegroundStyle.of(MusicPlayerView.SECONDARY_FG)),
                Text.of("🔊").with(FontSize.of(16)),
                Slider.of(volumeControl, 0f, 1f)
        );
        return VStack.of(HorizontalAlignment.CENTER, 0,
                // Centered groups over a full-width seek bar (the slider's FILL
                // width spans the bar via fill propagation; the groups stay
                // centered above it).
                groups,
                Slider.of(new PercentSeek(seek,
                                () -> MusicPlayerView.at(trackIndex.get()).duration()),
                        0f, 100f, seek::onEditingChanged)
                        .with(FrameMod.of(Float.POSITIVE_INFINITY), Padding.of(0, 16, 0, 16))
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