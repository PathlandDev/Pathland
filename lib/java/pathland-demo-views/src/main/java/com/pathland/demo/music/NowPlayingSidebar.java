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
        return VStack.with(v -> v.alignment(HorizontalAlignment.LEADING).spacing(14)).children(
                Text.with(t -> t.text("Now Playing")).modifiers(FontSize.with(s -> s.size(18)), FontWeight.BOLD)
                        .modifiers(AccessibilityRole.with(a -> a.role(Roles.HEADER))),
                Image.with(i -> i.source(computed(() -> MusicPlayerView.at(trackIndex.get()).cover())))
                        .modifiers(Frame.with(f -> f.width(220).height(220))).modifiers(ScaledToFill.with())
                        .modifiers(Border.with(b -> b.color(Color.BLACK).width(1).radius(8))),
                Text.with(t -> t.text(computed(() -> MusicPlayerView.at(trackIndex.get()).title())))
                        .modifiers(FontSize.with(s -> s.size(20)), FontWeight.BOLD),
                Text.with(t -> t.text(computed(() -> MusicPlayerView.at(trackIndex.get()).artist()
                        + " · " + MusicPlayerView.at(trackIndex.get()).album())))
                        .modifiers(ForegroundStyle.with(f -> f.color(MusicPlayerView.SECONDARY_FG))),
                Divider.modifiers(Padding.with(p -> p.edges(0, 0, 0, 10))),
                Text.with(t -> t.text("Up Next")).modifiers(FontWeight.SEMIBOLD),
                upNextRow(1),
                upNextRow(2)
        )
        .modifiers(Padding.with(p -> p.uniform(24)))
        .modifiers(Background.with(b -> b.color(MusicPlayerView.SIDEBAR_BG)))
        .modifiers(Frame.with(f -> f.width(MusicPlayerView.SIDEBAR_WIDTH).height(Commands.Size.FILL)));
    }

    /** The track {@code offset} places after the current one. */
    private View upNextRow(int offset) {
        return Text.with(t -> t.text(computed(() -> {
            Track track = MusicPlayerView.at(trackIndex.get() + offset);
            return (offset == 1 ? "Up next · " : "Then · ") + track.title() + " — " + track.artist();
        }))).modifiers(FontSize.with(s -> s.size(13)), ForegroundStyle.with(f -> f.color(MusicPlayerView.SECONDARY_FG)));
    }
}