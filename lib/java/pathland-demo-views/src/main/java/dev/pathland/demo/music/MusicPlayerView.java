package dev.pathland.demo.music;

import dev.pathland.view.*;
import dev.pathland.view.signal.Signal;
import dev.pathland.view.state.State;
import static dev.pathland.view.Modifiers.*;

import static dev.pathland.view.signal.Signals.computed;

import java.util.List;
import java.util.function.Consumer;

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
 *
 * <p>An optional {@code adUrl} (spec/PROXY.md) embeds a **second Pathland app**
 * — e.g. one mounted on the same server — as a sponsored slot in the Now Playing
 * pane via a {@link ProxyView}.
 */
public final class MusicPlayerView implements View, Configurable<MusicPlayerView.Config> {

    static final int SIDEBAR_WIDTH = 280;
    static final int BAR_HEIGHT = 76;

    // Gray-600: >= 4.5:1 on white, the sidebar, the selected row, and the player bar.
    static final Color SECONDARY_FG = Color.rgb(0x4B, 0x55, 0x63);
    static final Color ACTIVE_ROW_BG = Color.rgb(0xE8, 0xEF, 0xFF);
    static final Color ACTIVE_ROW_FG = Color.rgb(0x1A, 0x3A, 0x8C);
    static final Color SIDEBAR_BG = Color.rgb(0xF6, 0xF7, 0xFA);
    static final Color BAR_BORDER = Color.rgb(0xFF, 0xFF, 0xFF);
    static final Color BAR_BG = Color.argb(0xBF, 0xEB, 0xEB, 0xED);

    /** {@link MusicPlayerView} values. */
    public static final class Config implements View.Config {

        private String adUrl;

        /**
         * The base URL of a Pathland app to embed as a sponsored slot in the Now
         * Playing pane (spec/PROXY.md) — the music player proxies it. Absent = no
         * sponsored slot. App-configured, never client-supplied.
         */
        public Config adUrl(String adUrl) {
            this.adUrl = adUrl;
            return this;
        }
    }

    private final Config config;

    private MusicPlayerView(Config config) {
        this.config = config;
    }

    /** A player with no sponsored slot. */
    public static ViewBuilder<MusicPlayerView, Config> make() {
        return new ViewBuilder<>(new MusicPlayerView(new Config()));
    }

    /** A player with a sponsored slot proxying {@code adUrl} (spec/PROXY.md). */
    public static ViewBuilder<MusicPlayerView, Config> make(String adUrl) {
        Config config = new Config();
        config.adUrl(adUrl);
        return new ViewBuilder<>(new MusicPlayerView(config));
    }

    /** Configure the player's values. */
    public static ViewBuilder<MusicPlayerView, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new MusicPlayerView(config));
    }

    @Override
    public Config config() {
        return config;
    }

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

    // The playback controller (spec/MEDIA.md): a single state object owning the
    // transport. Wire it in body() to the persisted signals above; both the
    // compact PlayerBar and the wide NowPlayingSidebar (its SIZE_THAT_FITS
    // branches) drive the SAME controller, so a band swap never restarts playback.
    final MediaController player = new MediaController();

    /** The media ended: advance to the next track and restart from the top. */
    private void ended() {
        trackIndex.update(i -> (i + 1) % TRACKS.size());
        seekRequest.set(0f);
        position.set(0f);
    }

    @Override
    public View body() {
        // Bind the controller to this player's (persisted) transport state, so the
        // emitter drives exactly the signals the rest of the UI reads.
        player.source(computed(() -> at(trackIndex.get()).audio()));
        player.playing(playing.signal());
        player.position(seekRequest.signal());
        player.volume(volumeRequest.signal());
        player.time(position.signal());
        player.volumeReport(volume.signal());
        player.onEnded(this::ended);
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
        // The main play area (library + floating player bar + lyric pill) is
        // authored ONCE and wrapped in the same root component (HSTACK) for BOTH
        // fit candidates, so the reconcile re-uses every shared node and a fit
        // swap only inserts/removes the now-playing sidebar subtree — a handful
        // of TREE deltas instead of rebuilding the whole main area.
        var mainArea = ZStack
                .make(z -> z.alignment(Alignment.BOTTOM_CENTER))
                .children(
                        LibraryView.make(l -> l
                                .trackIndex(trackIndex.signal())
                                .position(position.signal())
                                .playing(playing.signal())
                                .seekRequest(seekRequest.signal())),
                        HStack.make().modifiers(padding(16))
                                .children(
                                        Spacer.make(),
                                        PlayerBar.make(p -> p
                                        .trackIndex(trackIndex.signal())
                                        .player(player))
                                        .modifiers(
                                                border(b -> b.color(MusicPlayerView.BAR_BORDER).width(1).radius(500)),
                                                shadow(10)),
                                        Spacer.make()
                                ),
                        subtitlePill(block, previous, current));
        var wideRow = HStack.make()
                .modifiers(frame(f -> f.width(Commands.Size.FILL).height(Commands.Size.FILL)))
                .children(
                        mainArea,
                        NowPlayingSidebar.make(s -> s
                                .trackIndex(trackIndex.signal())
                                .adUrl(config.adUrl)).modifiers(clipped()));
        var compactRow = HStack.make()
                .modifiers(frame(f -> f.width(Commands.Size.FILL).height(Commands.Size.FILL)))
                .children(mainArea);
        // Audio has no view: the attach marker materializes the playback
        // controller's node (non-visual) ONCE at the root, outside both the fit
        // slot and the shared main area. Keeping it outside the swapping branch
        // means a full snapshot (both candidates) never materializes a duplicate
        // controller id, and a band swap never tears the controller down.
        return VStack.make()
                .children(
                        player.attach(),
                        SizeThatFits.make(
                                Fit.of(wideRow, 1024f),
                                Fit.of(compactRow))
                                .modifiers(
                                        frame(f -> f.width(Commands.Size.FILL).height(Commands.Size.FILL)),
                                        accessibilityRole(Roles.MAIN)))
                .modifiers(
                        frame(f -> f.width(Commands.Size.FILL).height(Commands.Size.FILL)),
                        // Runtime window metadata: the tab/window title follows the
                        // current track (a computed → one META opcode per change).
                        windowMetadata(w -> w
                                .title(computed(() -> "Pathland · " + at(trackIndex.get()).title()))
                                .description(computed(() -> at(trackIndex.get()).artist()
                                        + " · " + at(trackIndex.get()).album()))
                                .locale("en")));
    }

    /** The karaoke lyric pill just above the floating player bar: always two
     *  italic lines — the line being sung (the one before the cue's new line)
     *  in bright white on top, the next line dimmed below it — on a translucent
     *  black pill; invisible (clear) when there is no block. Taps pass through. */
    private static View subtitlePill(Signal<List<String>> block, Signal<String> previous,
                                     Signal<Integer> current) {
        View currentLine = Text
                .make(computed(() -> {
                    String p = previous.get();
                    return p.isEmpty() ? lineAt(block.get(), current.get()) : p;
                }))
                .modifiers(
                        fontSize(13),
                        FontStyle.ITALIC,
                        foregroundStyle(Color.WHITE));
        View nextLine = Text
                .make(computed(() -> {
                    String p = previous.get();
                    return p.isEmpty() ? "" : lineAt(block.get(), current.get());
                }))
                .modifiers(
                        fontSize(13),
                        FontStyle.ITALIC,
                        foregroundStyle(Color.argb(170, 255, 255, 255)));
        return VStack.make(v -> v.alignment(HorizontalAlignment.CENTER).spacing(2))
                .modifiers(
                        padding(p -> p.edges(6, 12, 6, 12)),
                        cornerRadius(8),
                        offset(o -> o.x(0).y(-(BAR_HEIGHT + 24))),
                        allowsHitTesting(false),
                        background(computed(() -> block.get().isEmpty() || current.get() < 0
                                ? Color.CLEAR
                                : Color.argb(128, 0, 0, 0))))
                .children(currentLine, nextLine);
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