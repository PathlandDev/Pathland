package dev.pathland.demo.music

import dev.pathland.demo.music.player.PlayerMainArea
import dev.pathland.demo.music.service.MediaPlayerService
import dev.pathland.demo.music.sidebar.NowPlayingSidebar
import dev.pathland.view.core.Color
import dev.pathland.view.core.Roles
import dev.pathland.view.core.View
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.Fit
import dev.pathland.view.dsl.HStack
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.ViewThatFits
import dev.pathland.view.dsl.accessibilityRole
import dev.pathland.view.dsl.clipped
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.environment
import dev.pathland.view.dsl.frame
import dev.pathland.view.dsl.windowMetadata

/**
 * The music player demo root view — an Apple Music-style player authored purely
 * with the Kotlin DSL, composed of the [PlayerMainArea] (library + floating
 * player bar) and the wide-layout [NowPlayingSidebar].
 *
 * The root owns the per-session [MediaPlayerService] and scopes it through the
 * environment; child views resolve the service instead of receiving its signals
 * through their constructors.
 */
class MusicPlayerView @JvmOverloads constructor(
    private val adUrl: String? = null,
) : Component() {

    companion object {
        const val SIDEBAR_WIDTH = 280f
        const val BAR_HEIGHT = 76f

        // Gray-600: >= 4.5:1 on white, the sidebar, the selected row, and the player bar.
        val SECONDARY_FG: Color = Color.rgb(0x4B, 0x55, 0x63)
        val ACTIVE_ROW_BG: Color = Color.rgb(0xE8, 0xEF, 0xFF)
        val ACTIVE_ROW_FG: Color = Color.rgb(0x1A, 0x3A, 0x8C)
        val SIDEBAR_BG: Color = Color.rgb(0xF6, 0xF7, 0xFA)
        val BAR_BORDER: Color = Color.rgb(0xFF, 0xFF, 0xFF)
        val BAR_BG: Color = Color.argb(0xBF, 0xEB, 0xEB, 0xED)

        /** A player with no sponsored slot (Java-compatible factory). */
        @JvmStatic
        fun make(): MusicPlayerView = MusicPlayerView()

        /** A player with a sponsored slot proxying [adUrl] (Java-compatible factory). */
        @JvmStatic
        fun make(adUrl: String?): MusicPlayerView = MusicPlayerView(adUrl)
    }

    private val service by lazy { MediaPlayerService() }

    override fun body(): View {
        val mediaPlayer = service

        // One root fit slot selects the complete layout for the window width:
        // narrow (no volume, compact transport), medium (volume), wide (volume +
        // now-playing sidebar). Keeping the breakpoint at the root avoids a
        // nested fit slot whose wide-only children could survive a candidate swap.
        val narrowRow = HStack {
            PlayerMainArea(wide = false)
        }.frame(width = Commands.Size.FILL, height = Commands.Size.FILL)

        val mediumRow = HStack {
            PlayerMainArea(wide = true)
        }.frame(width = Commands.Size.FILL, height = Commands.Size.FILL)

        val wideRow = HStack {
            PlayerMainArea(wide = true)
            NowPlayingSidebar(adUrl).clipped()
        }.frame(width = Commands.Size.FILL, height = Commands.Size.FILL)

        return VStack {
            ViewThatFits(
                Fit(narrowRow),
                Fit(mediumRow, 700f),
                Fit(wideRow, 1024f),
            ).frame(width = Commands.Size.FILL, height = Commands.Size.FILL)
                .accessibilityRole(Roles.MAIN)
        }.frame(width = Commands.Size.FILL, height = Commands.Size.FILL)
            .environment(MediaPlayerService.KEY, mediaPlayer)
            .windowMetadata {
                title(computed { "Pathland · " + mediaPlayer.currentTrack.get().title })
                description(computed {
                    mediaPlayer.currentTrack.get().artist + " · " + mediaPlayer.currentTrack.get().album
                })
                locale("en")
            }
    }
}
