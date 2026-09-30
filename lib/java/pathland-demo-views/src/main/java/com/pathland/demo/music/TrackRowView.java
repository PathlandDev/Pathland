package com.pathland.demo.music;

import com.pathland.view.*;
import com.pathland.view.signal.WritableSignal;

import static com.pathland.view.signal.Signals.computed;

/**
 * A single track row: a cover thumbnail, the title/artist, and the duration.
 * Selecting it starts that track through the app-owned player state, and the
 * current row is highlighted reactively via a computed background/foreground.
 */
public final class TrackRowView implements View {

    private final Track track;
    private final int index;
    private final WritableSignal<Integer> trackIndex;
    private final WritableSignal<Float> position;
    private final WritableSignal<Boolean> playing;

    /**
     * @param track      the track this row represents
     * @param index      the track's index in the library
     * @param trackIndex the current track index (drives the highlight)
     * @param position   the play position (seconds), reset on selection
     * @param playing    set to true when the row is selected
     */
    public TrackRowView(Track track, int index, WritableSignal<Integer> trackIndex,
                        WritableSignal<Float> position, WritableSignal<Boolean> playing) {
        this.track = track;
        this.index = index;
        this.trackIndex = trackIndex;
        this.position = position;
        this.playing = playing;
    }

    @Override
    public View body() {
        var isCurrent = computed(() -> MusicPlayerView.at(trackIndex.get()) == track);
        var bg = computed(() -> isCurrent.get() ? MusicPlayerView.ACTIVE_ROW_BG : Color.CLEAR);
        var fg = computed(() -> isCurrent.get() ? MusicPlayerView.ACTIVE_ROW_FG : Color.BLACK);
        return Button.of(
                HStack.of(Alignment.CENTER, 12f,
                        Image.of(track.cover()).with(FrameMod.of(44, 44), ScaledToFit.of()),
                        VStack.of(Alignment.LEADING, 2,
                                Text.of(track.title()).with(FontWeightMod.of(FontWeight.SEMIBOLD)),
                                Text.of(track.artist() + " · " + track.album())
                                        .with(FontSize.of(13),
                                                ForegroundStyle.of(MusicPlayerView.SECONDARY_FG))
                        ),
                        Spacer.of(),
                        Text.of(MusicPlayerView.fmt(track.duration()))
                                .with(FontSize.of(13), ForegroundStyle.of(MusicPlayerView.SECONDARY_FG))
                ),
                () -> {
                    trackIndex.set(index);
                    position.set(0f);
                    playing.set(true);
                })
                .with(Background.of(bg), ForegroundStyle.of(fg))
                .with(Padding.of(6))
                // Full-width row: the layout contract's cross-axis default is hug
                // (LAYOUT.md), so a row that should span the library column must
                // explicitly FILL its width.
                .with(FrameMod.of(Commands.Size.FILL));
    }
}