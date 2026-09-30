package com.pathland.demo.music;

import com.pathland.view.*;
import com.pathland.view.signal.WritableSignal;

import java.util.ArrayList;
import java.util.List;

/**
 * The library pane: the "Library" header and the full track list. Each row is a
 * {@link TrackRowView}; selecting one starts that track through the app-owned
 * player state (owned by the root view).
 */
public final class LibraryView implements View {

    private final WritableSignal<Integer> trackIndex;
    private final WritableSignal<Float> position;
    private final WritableSignal<Boolean> playing;

    /**
     * @param trackIndex the current track index
     * @param position   the play position (seconds), reset on selection
     * @param playing    whether a track is playing
     */
    public LibraryView(WritableSignal<Integer> trackIndex, WritableSignal<Float> position,
                       WritableSignal<Boolean> playing) {
        this.trackIndex = trackIndex;
        this.position = position;
        this.playing = playing;
    }

    @Override
    public View body() {
        List<View> rows = new ArrayList<>();
        for (int i = 0; i < MusicPlayerView.TRACKS.size(); i++) {
            rows.add(new TrackRowView(MusicPlayerView.TRACKS.get(i), i, trackIndex, position, playing));
        }
        // A trailing spacer absorbs the leftover vertical space.
        rows.add(Spacer.of());
        
        return VStack.of(
                Text.of("Library").with(FontSize.of(26), FontWeightMod.of(FontWeight.BOLD))
                        .with(AccessibilityRole.of(Roles.HEADER)),
                Text.of(MusicPlayerView.TRACKS.size() + " songs · " + MusicPlayerView.TRACKS.size() + " albums")
                        .with(ForegroundStyle.of(MusicPlayerView.SECONDARY_FG)),
                Divider.of().with(Padding.of(0, 0, 0, 10)),
                ScrollView.of(
                    VStack.of(rows)
                )
        )
        .with(Padding.of(24))
        .with(FrameMod.of(Commands.Size.FILL, Commands.Size.FILL));
    }
}