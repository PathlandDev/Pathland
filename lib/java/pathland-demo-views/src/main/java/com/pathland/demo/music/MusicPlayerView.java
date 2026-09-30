package com.pathland.demo.music;

import com.pathland.view.*;
import com.pathland.view.state.State;

import java.util.List;

/**
 * The music player demo root view — an Apple Music-style player authored purely
 * with the Java DSL, composed of smaller views: the track {@link LibraryView}
 * (left), the {@link NowPlayingSidebar} with the album art (right), and the
 * bottom {@link PlayerBar} with transport controls, a seek slider, and a volume
 * slider.
 *
 * <p>The root owns the player's {@link State} (persisted per-session) and hands
 * the bound signals to its subviews; selecting a track or pressing prev/next
 * only changes app-owned state, and the renderer re-emits fine-grained
 * {@code SET_TEXT}/{@code SET_PROPERTY} deltas.
 */
public final class MusicPlayerView implements View {

    static final int SIDEBAR_WIDTH = 280;
    static final int BAR_HEIGHT = 76;

    static final Color SECONDARY_FG = Color.rgb(0x6B, 0x72, 0x80);
    static final Color ACTIVE_ROW_BG = Color.rgb(0xE8, 0xEF, 0xFF);
    static final Color ACTIVE_ROW_FG = Color.rgb(0x1A, 0x3A, 0x8C);
    static final Color SIDEBAR_BG = Color.rgb(0xF6, 0xF7, 0xFA);
    static final Color BAR_BORDER = Color.rgb(0xE2, 0xE8, 0xF0);
    static final Color BAR_BG = Color.rgb(0xFB, 0xFB, 0xFD);

    /** The demo library: six Pathland concept tracks, one album each. */
    static final List<Track> TRACKS = List.of(
            new Track("Building on Solid Ground", "The Foundation", "The Open Horizon", 178f,
                    "/_pathland/assets/albumart/cover1.jpg",
                    "/_pathland/assets/audio/track1.mp3"),
            new Track("Pathland Crossing", "The Crossings", "Native Operations", 166f,
                    "/_pathland/assets/albumart/cover2.jpg",
                    "/_pathland/assets/audio/track2.mp3"),
            new Track("Rendered Free", "Rena Render", "Grace From The Source", 182f,
                    "/_pathland/assets/albumart/cover3.jpg",
                    "/_pathland/assets/audio/track3.mp3"),
            new Track("Rendered In Your Arms", "Ember Frame", "Architectures of Grace", 160f,
                    "/_pathland/assets/albumart/cover4.jpg",
                    "/_pathland/assets/audio/track4.mp3"),
            new Track("Sixty Frames Per Second", "Rowan Frame", "The Protocol Sessions", 164f,
                    "/_pathland/assets/albumart/cover5.jpg",
                    "/_pathland/assets/audio/track5.mp3"),
            new Track("The Pathland Dream", "Neon Protocol", "Velvet Horizons", 170f,
                    "/_pathland/assets/albumart/cover6.jpg",
                    "/_pathland/assets/audio/track6.mp3"));

    // App-owned, persisted per-session (the annotation processor wires State fields
    // by type; explicit keys keep the player's state scope stable).
    State<Float> position = new State<>(0f, "player.position");
    State<Boolean> playing = new State<>(false, "player.playing");
    State<Integer> trackIndex = new State<>(0, "player.track");
    State<Float> volume = new State<>(0.7f, "player.volume");

    @Override
    public View body() {
        return VStack.of(
                HStack.of(
                        new LibraryView(trackIndex.signal(), position.signal(), playing.signal()),
                        new NowPlayingSidebar(trackIndex.signal())
                ).with(FrameMod.of(Commands.Size.FILL, Commands.Size.FILL)),
                new PlayerBar(trackIndex.signal(), position.signal(), playing.signal(), volume.signal())
        )
        .with(FrameMod.of(Commands.Size.FILL, Commands.Size.FILL))
        .with(AccessibilityRole.of(Roles.MAIN));
    }

    /** The track at an index (wraps around; safe for any signed index). */
    static Track at(int index) {
        return TRACKS.get(Math.floorMod(index, TRACKS.size()));
    }

    /** Format seconds as {@code m:ss}. */
    static String fmt(float seconds) {
        int s = Math.max(0, Math.round(seconds));
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}