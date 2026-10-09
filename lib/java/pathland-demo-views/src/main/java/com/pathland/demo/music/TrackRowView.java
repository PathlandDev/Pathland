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
    private final WritableSignal<Float> seekRequest;

    /**
     * @param track       the track this row represents
     * @param index       the track's index in the library
     * @param trackIndex  the current track index (drives the highlight)
     * @param position    the play position (seconds), reset on selection
     * @param playing     set to true when the row is selected
     * @param seekRequest the seek command signal (seconds), reset on selection so
     *                    the newly-selected track starts from the beginning
     */
    public TrackRowView(Track track, int index, WritableSignal<Integer> trackIndex,
                        WritableSignal<Float> position, WritableSignal<Boolean> playing,
                        WritableSignal<Float> seekRequest) {
        this.track = track;
        this.index = index;
        this.trackIndex = trackIndex;
        this.position = position;
        this.playing = playing;
        this.seekRequest = seekRequest;
    }

    @Override
    public View body() {
        var isCurrent = computed(() -> MusicPlayerView.at(trackIndex.get()) == track);
        var bg = computed(() -> isCurrent.get() ? MusicPlayerView.ACTIVE_ROW_BG : Color.CLEAR);
        var fg = computed(() -> isCurrent.get() ? MusicPlayerView.ACTIVE_ROW_FG : Color.BLACK);
        return Button.with(b -> b.action(() -> {
                    trackIndex.set(index);
                    position.set(0f);
                    seekRequest.set(0f);
                    playing.set(true);
                })).children(
                HStack.with(h -> h.alignment(VerticalAlignment.CENTER).spacing(12f)).children(
                        Image.with(i -> i.source(track.cover())).modifiers(Frame.with(f -> f.width(44).height(44))).modifiers(ScaledToFit.with()),
                        VStack.with(v -> v.alignment(HorizontalAlignment.LEADING).spacing(2)).children(
                                Text.with(t -> t.text(track.title())).modifiers(FontWeight.SEMIBOLD),
                                Text.with(t -> t.text(track.artist() + " · " + track.album()))
                                        .modifiers(FontSize.with(s -> s.size(13)),
                                                ForegroundStyle.with(f -> f.color(MusicPlayerView.SECONDARY_FG)))
                        ),
                        Spacer.modifiers(),
                        Text.with(t -> t.text(MusicPlayerView.fmt(track.duration())))
                                .modifiers(FontSize.with(s -> s.size(13)), ForegroundStyle.with(f -> f.color(MusicPlayerView.SECONDARY_FG)))
                ))
                .modifiers(Background.with(b -> b.color(bg)), ForegroundStyle.with(f -> f.color(fg)))
                .modifiers(Padding.with(p -> p.uniform(6)))
                // Full-width row: the layout contract's cross-axis default is hug
                // (LAYOUT.md), so a row that should span the library column must
                // explicitly FILL its width.
                .modifiers(Frame.ofWidth(Commands.Size.FILL));
    }
}