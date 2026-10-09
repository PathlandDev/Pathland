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
    private final WritableSignal<Float> seekRequest;

    /**
     * @param trackIndex the current track index
     * @param position   the play position (seconds), reset on selection
     * @param playing    whether a track is playing
     * @param seekRequest the seek command signal (seconds), reset on selection so
     *                    the newly-selected track starts from the beginning
     */
    public LibraryView(WritableSignal<Integer> trackIndex, WritableSignal<Float> position,
                       WritableSignal<Boolean> playing, WritableSignal<Float> seekRequest) {
        this.trackIndex = trackIndex;
        this.position = position;
        this.playing = playing;
        this.seekRequest = seekRequest;
    }

    @Override
    public View body() {
        List<View> rows = new ArrayList<>();
        for (int i = 0; i < MusicPlayerView.TRACKS.size(); i++) {
            rows.add(new TrackRowView(MusicPlayerView.TRACKS.get(i), i, trackIndex, position, playing, seekRequest));
        }
        // A trailing spacer absorbs the leftover vertical space.
        rows.add(Spacer.modifiers());

        return VStack.children(
                Text.with(t -> t.text("Library")).modifiers(FontSize.with(s -> s.size(26)), FontWeight.BOLD)
                        .modifiers(AccessibilityRole.with(a -> a.role(Roles.HEADER))),
                Text.with(t -> t.text(MusicPlayerView.TRACKS.size() + " songs · " + MusicPlayerView.TRACKS.size() + " albums"))
                        .modifiers(ForegroundStyle.with(f -> f.color(MusicPlayerView.SECONDARY_FG))),
                Divider.modifiers(Padding.with(p -> p.edges(0, 0, 0, 10))),
                ScrollView.children(
                    VStack.children(rows.toArray(new View[0]))
                )
        )
        .modifiers(Padding.with(p -> p.uniform(24)))
        .modifiers(Frame.with(f -> f.width(Commands.Size.FILL).height(Commands.Size.FILL)));
    }
}