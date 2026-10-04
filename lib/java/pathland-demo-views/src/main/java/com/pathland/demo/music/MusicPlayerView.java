package com.pathland.demo.music;

import com.pathland.view.*;
import com.pathland.view.signal.Signal;
import com.pathland.view.state.State;

import static com.pathland.view.signal.Signals.computed;

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
    static final Color BAR_BG = Color.argb(0xBF, 0xFB, 0xFB, 0xFD);

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
                    "/_pathland/assets/audio/track6.mp3"),
            new Track("Across the Open Land", "Open Land", "The Weight of Open Air", 156f,
                    "/_pathland/assets/albumart/cover7.jpg",
                    "/_pathland/assets/audio/track7.mp3"),
            new Track("Sixteen Bytes", "The Byte Ensemble", "The Ring Buffer Sessions", 182f,
                    "/_pathland/assets/albumart/cover8.jpg",
                    "/_pathland/assets/audio/track8.mp3"));

    // App-owned, persisted per-session (the annotation processor wires State fields
    // by type; explicit keys keep the player's state scope stable).
    State<Float> position = new State<>(0f, "player.position");
    State<Boolean> playing = new State<>(false, "player.playing");
    State<Integer> trackIndex = new State<>(0, "player.track");
    State<Float> volume = new State<>(0.7f, "player.volume");
    // The SEEK / VOLUME commands (bound to MEDIA_POSITION / MEDIA_VOLUME) —
    // written only on user interaction, decoupled from the display signals above
    // so a MEDIA_TIME_UPDATED / MEDIA_VOLUME_CHANGED report never echoes back as
    // a seek command (spec/EVENTS.md Media).
    State<Float> seekRequest = new State<>(0f, "player.seekRequest");
    State<Float> volumeRequest = new State<>(0.7f, "player.volumeRequest");

    @Override
    public View body() {
        // The synced karaoke lyric block + the current (being-sung) line index
        // for the current track + play position, shown only while playing.
        var block = computed(() -> playing.get()
                ? Lyrics.blockFor(at(trackIndex.get()).title(), position.get())
                : List.<String>of());
        var current = computed(() -> playing.get()
                ? Lyrics.currentIndex(at(trackIndex.get()).title(), position.get())
                : -1);
        var previous = computed(() -> playing.get()
                ? Lyrics.previousLine(at(trackIndex.get()).title(), position.get())
                : "");
        return ZStack.of(Alignment.BOTTOM_CENTER,
                HStack.of(
                        new LibraryView(trackIndex.signal(), position.signal(), playing.signal(), seekRequest.signal()),
                        new NowPlayingSidebar(trackIndex.signal()).with(Clipped.of())
                ).with(FrameMod.of(Commands.Size.FILL, Commands.Size.FILL)),
                HStack.of(
                    new PlayerBar(trackIndex.signal(), position.signal(), playing.signal(), volume.signal(),
                        seekRequest.signal(), volumeRequest.signal())
                        .with(Border.of(Color.rgb(200,200,200), 1, 16))
                ).with(Padding.of(16)),
                subtitlePill(block, previous, current)
        )
        .with(FrameMod.of(Commands.Size.FILL, Commands.Size.FILL))
        .with(AccessibilityRole.of(Roles.MAIN));
    }

    /** The karaoke lyric pill just above the floating player bar: always two
     *  italic lines — the line being sung (the one before the cue's new line)
     *  in bright white on top, the next line dimmed below it — on a translucent
     *  black pill; invisible (clear) when there is no block. Taps pass through. */
    private static View subtitlePill(Signal<List<String>> block, Signal<String> previous,
                                     Signal<Integer> current) {
        View currentLine = Text.of(computed(() -> {
            String p = previous.get();
            return p.isEmpty() ? lineAt(block.get(), current.get()) : p;
        }))
                .with(FontSize.of(13))
                .with(FontStyleMod.of(FontStyle.ITALIC))
                .with(ForegroundStyle.of(Color.WHITE));
        View nextLine = Text.of(computed(() -> {
            String p = previous.get();
            return p.isEmpty() ? "" : lineAt(block.get(), current.get());
        }))
                .with(FontSize.of(13))
                .with(FontStyleMod.of(FontStyle.ITALIC))
                .with(ForegroundStyle.of(Color.argb(170, 255, 255, 255)));
        return VStack.of(HorizontalAlignment.CENTER, 2, currentLine, nextLine)
                .with(Padding.of(6, 12, 6, 12))
                .with(CornerRadius.of(8))
                .with(Offset.of(0, -(BAR_HEIGHT + 24)))
                .with(AllowsHitTesting.of(false))
                .with(Background.of(computed(() -> block.get().isEmpty() || current.get() < 0
                        ? Color.CLEAR
                        : Color.argb(128, 0, 0, 0))));
    }

    /** The row text for a block index (empty beyond the block). */
    private static String lineAt(List<String> block, int index) {
        return index >= 0 && index < block.size() ? block.get(index) : "";
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