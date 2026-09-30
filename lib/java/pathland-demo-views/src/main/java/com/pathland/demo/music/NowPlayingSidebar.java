package com.pathland.demo.music;

import com.pathland.view.*;
import com.pathland.view.signal.WritableSignal;

import static com.pathland.view.signal.Signals.computed;

/**
 * The now-playing sidebar (right): the current track's large album art, its
 * title/artist, and the next two tracks. Everything is bound to the current
 * {@code trackIndex}, so a track change re-emits only this pane's deltas.
 */
public final class NowPlayingSidebar implements View {

    private final WritableSignal<Integer> trackIndex;

    /**
     * @param trackIndex the current track index (drives the art + up-next)
     */
    public NowPlayingSidebar(WritableSignal<Integer> trackIndex) {
        this.trackIndex = trackIndex;
    }

    @Override
    public View body() {
        return VStack.of(Alignment.LEADING, 14,
                Text.of("Now Playing").with(FontSize.of(18), FontWeightMod.of(FontWeight.BOLD))
                        .with(AccessibilityRole.of(Roles.HEADER)),
                Image.of(computed(() -> MusicPlayerView.at(trackIndex.get()).cover()))
                        .with(FrameMod.of(220, 220), ScaledToFill.of())
                        .with(Border.of(Color.BLACK, 1, 8)),
                Text.of(computed(() -> MusicPlayerView.at(trackIndex.get()).title()))
                        .with(FontSize.of(20), FontWeightMod.of(FontWeight.BOLD)),
                Text.of(computed(() -> MusicPlayerView.at(trackIndex.get()).artist()
                        + " · " + MusicPlayerView.at(trackIndex.get()).album()))
                        .with(ForegroundStyle.of(MusicPlayerView.SECONDARY_FG)),
                Divider.of().with(Padding.of(0, 0, 0, 10)),
                Text.of("Up Next").with(FontWeightMod.of(FontWeight.SEMIBOLD)),
                upNextRow(1),
                upNextRow(2)
        )
        .with(Padding.of(24))
        .with(Background.of(MusicPlayerView.SIDEBAR_BG))
        .with(FrameMod.of(MusicPlayerView.SIDEBAR_WIDTH, Commands.Size.FILL));
    }

    /** The track {@code offset} places after the current one. */
    private View upNextRow(int offset) {
        return Text.of(computed(() -> {
            Track t = MusicPlayerView.at(trackIndex.get() + offset);
            return (offset == 1 ? "Up next · " : "Then · ") + t.title() + " — " + t.artist();
        })).with(FontSize.of(13), ForegroundStyle.of(MusicPlayerView.SECONDARY_FG));
    }
}