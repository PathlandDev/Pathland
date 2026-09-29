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

    private PlayerControlsStyle(WritableSignal<Integer> trackIndex) {
        this.trackIndex = trackIndex;
    }

    /** A player-controls style over {@code trackIndex} (drives prev/next + the now-playing content). */
    public static PlayerControlsStyle of(WritableSignal<Integer> trackIndex) {
        return new PlayerControlsStyle(trackIndex);
    }

    @Override
    public View makeBody(Configuration config) {
        var current = computed(() -> MusicPlayerView.at(trackIndex.get()));
        var glyph = computed(() -> config.playing().get() ? "⏸" : "▶");
        var groups = HStack.of(Alignment.CENTER, 18f,
                Button.of("⏮", () -> { prev(); config.position().set(0f); }).with(FontSize.of(20)),
                Button.of(Text.of(glyph), () -> config.playing().update(v -> !v)).with(FontSize.of(28)),
                Button.of("⏭", () -> { next(); config.position().set(0f); }).with(FontSize.of(20)),
                Text.of("|").with(FontSize.of(18), ForegroundStyle.of(MusicPlayerView.SECONDARY_FG)),
                Image.of(computed(() -> current.get().cover()))
                        .with(FrameMod.of(36, 36), ScaledToFit.of()),
                VStack.of(Alignment.LEADING, 2,
                        Text.of(computed(() -> current.get().title()))
                                .with(FontWeightMod.of(FontWeight.SEMIBOLD), LineLimit.of(1)),
                        Text.of(computed(() -> current.get().artist()))
                                .with(FontSize.of(13), ForegroundStyle.of(MusicPlayerView.SECONDARY_FG), LineLimit.of(1))
                ).with(FrameMod.of(160f)),
                Text.of("|").with(FontSize.of(18), ForegroundStyle.of(MusicPlayerView.SECONDARY_FG)),
                Text.of("🔊").with(FontSize.of(16)),
                Slider.of(config.volume(), 0f, 1f)
        );
        return VStack.of(Alignment.CENTER, 0,
                // Center the three groups in the full-width bar.
                HStack.of(
                    Spacer.of(),
                    VStack.of(groups,
                            Slider.of(new PercentSeek(config.position(),
                                    () -> MusicPlayerView.at(trackIndex.get()).duration()),
                                0f, 100f)),
                    Spacer.of()
                )
        );
    }

    /** Previous track (wraps around); the seek restarts via the position binding. */
    private void prev() {
        trackIndex.update(i -> (i - 1 + MusicPlayerView.TRACKS.size()) % MusicPlayerView.TRACKS.size());
    }

    /** Next track (wraps around); the seek restarts via the position binding. */
    private void next() {
        trackIndex.update(i -> (i + 1) % MusicPlayerView.TRACKS.size());
    }
}