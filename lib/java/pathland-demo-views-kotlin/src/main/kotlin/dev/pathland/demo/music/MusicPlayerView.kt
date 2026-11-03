package dev.pathland.demo.music

import dev.pathland.view.core.Alignment
import dev.pathland.view.core.Color as JColor
import dev.pathland.view.core.FontStyle
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.MediaController
import dev.pathland.view.core.Roles
import dev.pathland.view.core.View
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.dsl.Fit
import dev.pathland.view.dsl.HStack
import dev.pathland.view.dsl.KotlinView
import dev.pathland.view.dsl.Spacer
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.unaryPlus
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.ViewThatFits
import dev.pathland.view.dsl.ZStack
import dev.pathland.view.dsl.allowsHitTesting
import dev.pathland.view.dsl.background
import dev.pathland.view.dsl.border
import dev.pathland.view.dsl.clipped
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.cornerRadius
import dev.pathland.view.dsl.fontStyle
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.foregroundStyle
import dev.pathland.view.dsl.frame
import dev.pathland.view.dsl.offset
import dev.pathland.view.dsl.padding
import dev.pathland.view.dsl.shadow
import dev.pathland.view.dsl.windowMetadata
import dev.pathland.view.dsl.accessibilityRole

/**
 * The music player demo root view — an Apple Music-style player authored purely
 * with the Kotlin DSL, composed of the track [LibraryView] (left), the
 * [NowPlayingSidebar] with the album art (right), and the bottom [PlayerBar].
 *
 * <p>The root owns the player's persisted state and hands the bound signals to
 * its subviews; selecting a track or pressing prev/next only changes app-owned
 * state, and the renderer re-emits fine-grained deltas.
 */
class MusicPlayerView @JvmOverloads constructor(
    private val adUrl: String? = null,
) : KotlinView() {

    companion object {
        const val SIDEBAR_WIDTH = 280f
        const val BAR_HEIGHT = 76f

        // Gray-600: >= 4.5:1 on white, the sidebar, the selected row, and the player bar.
        val SECONDARY_FG: JColor = JColor.rgb(0x4B, 0x55, 0x63)
        val ACTIVE_ROW_BG: JColor = JColor.rgb(0xE8, 0xEF, 0xFF)
        val ACTIVE_ROW_FG: JColor = JColor.rgb(0x1A, 0x3A, 0x8C)
        val SIDEBAR_BG: JColor = JColor.rgb(0xF6, 0xF7, 0xFA)
        val BAR_BORDER: JColor = JColor.rgb(0xFF, 0xFF, 0xFF)
        val BAR_BG: JColor = JColor.argb(0xBF, 0xEB, 0xEB, 0xED)

        /** The demo library: eight Pathland concept tracks, one album each. */
        internal val TRACKS: List<Track> = listOf(
            Track(
                "Building on Solid Ground", "The Foundation", "The Open Horizon", 178f,
                "/_pathland/assets/albumart/cover1.jpg",
                "/_pathland/assets/audio/track1.mp3",
            ),
            Track(
                "Pathland Crossing", "The Crossings", "Native Operations", 166f,
                "/_pathland/assets/albumart/cover2.jpg",
                "/_pathland/assets/audio/track2.mp3",
            ),
            Track(
                "Rendered Free", "Rena Render", "Grace From The Source", 182f,
                "/_pathland/assets/albumart/cover3.jpg",
                "/_pathland/assets/audio/track3.mp3",
            ),
            Track(
                "Rendered In Your Arms", "Ember Frame", "Architectures of Grace", 160f,
                "/_pathland/assets/albumart/cover4.jpg",
                "/_pathland/assets/audio/track4.mp3",
            ),
            Track(
                "Sixty Frames Per Second", "Rowan Frame", "The Protocol Sessions", 164f,
                "/_pathland/assets/albumart/cover5.jpg",
                "/_pathland/assets/audio/track5.mp3",
            ),
            Track(
                "The Pathland Dream", "Neon Protocol", "Velvet Horizons", 170f,
                "/_pathland/assets/albumart/cover6.jpg",
                "/_pathland/assets/audio/track6.mp3",
            ),
            Track(
                "Across the Open Land", "Open Land", "The Weight of Open Air", 156f,
                "/_pathland/assets/albumart/cover7.jpg",
                "/_pathland/assets/audio/track7.mp3",
            ),
            Track(
                "Sixteen Bytes", "The Byte Ensemble", "The Ring Buffer Sessions", 182f,
                "/_pathland/assets/albumart/cover8.jpg",
                "/_pathland/assets/audio/track8.mp3",
            ),
        )

        /** A player with no sponsored slot (Java-compatible factory). */
        @JvmStatic
        fun make(): MusicPlayerView = MusicPlayerView()

        /** A player with a sponsored slot proxying [adUrl] (Java-compatible factory). */
        @JvmStatic
        fun make(adUrl: String?): MusicPlayerView = MusicPlayerView(adUrl)

        /** The track at an index (wraps around; safe for any signed index). */
        internal fun at(index: Int): Track = TRACKS[Math.floorMod(index, TRACKS.size)]

        /** Format seconds as `m:ss`. */
        internal fun fmt(seconds: Float): String {
            val s = maxOf(0, Math.round(seconds))
            return (s / 60).toString() + ":" + String.format("%02d", s % 60)
        }
    }

    private val position = mutableState(0f, "player.position")
    private val playing = mutableState(false, "player.playing")
    private val trackIndex = mutableState(0, "player.track")
    private val volume = mutableState(0.7f, "player.volume")

    // The SEEK / VOLUME commands (bound to MEDIA_POSITION / MEDIA_VOLUME) are
    // written only on user interaction, decoupled from the display signals above.
    private val seekRequest = mutableState(0f, "player.seekRequest")
    private val volumeRequest = mutableState(0.7f, "player.volumeRequest")

    // The playback controller (spec/MEDIA.md): a single state object owning the
    // transport. Both the compact PlayerBar and the wide NowPlayingSidebar drive
    // the SAME controller, so a band swap never restarts playback.
    private val player = MediaController()

    private fun ended() {
        trackIndex.set((trackIndex.get() + 1) % TRACKS.size)
        seekRequest.set(0f)
        position.set(0f)
    }

    override fun body(): View {
        player.source(computed { at(trackIndex.get()).audio })
        player.playing(playing)
        player.position(seekRequest)
        player.volume(volumeRequest)
        player.time(position)
        player.volumeReport(volume)
        player.onEnded { ended() }

        val block = computed {
            if (playing.get()) Lyrics.blockFor(at(trackIndex.get()).title, position.get()) else emptyList()
        }
        val current = computed {
            if (playing.get()) Lyrics.currentIndex(at(trackIndex.get()).title, position.get()) else -1
        }
        val previous = computed {
            if (playing.get()) Lyrics.previousLine(at(trackIndex.get()).title, position.get()) else ""
        }

        // Authored ONCE and wrapped in the same root component (HSTACK) for both
        // fit candidates, so a fit swap only inserts/removes the sidebar subtree.
        val mainArea = ZStack(alignment = Alignment.BOTTOM_CENTER) {
            LibraryView(trackIndex, position, playing, seekRequest)
            HStack {
                Spacer()
                PlayerBar(trackIndex, player)
                    .border {
                        color(BAR_BORDER)
                        width(1f)
                        radius(500f)
                    }
                    .shadow(10f)
                Spacer()
            }.padding(16f)
            subtitlePill(block, previous, current)
        }

        val wideRow = HStack {
            +mainArea
            NowPlayingSidebar(trackIndex, adUrl).clipped()
        }.frame(width = Commands.Size.FILL, height = Commands.Size.FILL)

        val compactRow = HStack {
            +mainArea
        }.frame(width = Commands.Size.FILL, height = Commands.Size.FILL)

        return VStack {
            +player.attach()
            ViewThatFits(
                Fit(wideRow, 1024f),
                Fit(compactRow),
            ).frame(width = Commands.Size.FILL, height = Commands.Size.FILL)
                .accessibilityRole(Roles.MAIN)
        }.frame(width = Commands.Size.FILL, height = Commands.Size.FILL)
            .windowMetadata {
                title(computed { "Pathland · " + at(trackIndex.get()).title })
                description(computed {
                    at(trackIndex.get()).artist + " · " + at(trackIndex.get()).album
                })
                locale("en")
            }
    }

    /**
     * The karaoke lyric pill just above the floating player bar: always two
     * italic lines on a translucent black pill; invisible when there is no block.
     */
    private fun subtitlePill(
        block: dev.pathland.view.core.signal.Signal<List<String>>,
        previous: dev.pathland.view.core.signal.Signal<String>,
        current: dev.pathland.view.core.signal.Signal<Int>,
    ): View = VStack(alignment = HorizontalAlignment.CENTER, spacing = 2f) {
        Text(computed {
            val p = previous.get()
            if (p.isEmpty()) lineAt(block.get(), current.get()) else p
        }).fontSize(13f)
            .fontStyle(FontStyle.ITALIC)
            .foregroundStyle(JColor.WHITE)

        Text(computed {
            val p = previous.get()
            if (p.isEmpty()) "" else lineAt(block.get(), current.get())
        }).fontSize(13f)
            .fontStyle(FontStyle.ITALIC)
            .foregroundStyle(JColor.argb(170, 255, 255, 255))
    }.padding(top = 6f, right = 12f, bottom = 6f, left = 12f)
        .cornerRadius(8f)
        .offset(x = 0f, y = -(BAR_HEIGHT + 24f))
        .allowsHitTesting(false)
        .background(computed {
            if (block.get().isEmpty() || current.get() < 0) JColor.CLEAR else JColor.argb(128, 0, 0, 0)
        })

    /** The row text for a block index (empty beyond the block). */
    private fun lineAt(block: List<String>, index: Int): String =
        if (index in block.indices) block[index] else ""
}
