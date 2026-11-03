package dev.pathland.demo.music

import dev.pathland.view.core.Color as JColor
import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.Roles
import dev.pathland.view.core.View
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.dsl.Divider
import dev.pathland.view.dsl.Image
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.ProxyView
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.accessibilityRole
import dev.pathland.view.dsl.background
import dev.pathland.view.dsl.border
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.fontWeight
import dev.pathland.view.dsl.foregroundStyle
import dev.pathland.view.dsl.frame
import dev.pathland.view.dsl.padding
import dev.pathland.view.dsl.scaledToFill

/**
 * The now-playing sidebar (right): the current track's large album art, its
 * title/artist, and the next two tracks. An optional [adUrl] appends a
 * sponsored slot embedding a second Pathland app via a ProxyView.
 */
internal class NowPlayingSidebar(
    private val trackIndex: WritableSignal<Int>,
    private val adUrl: String?,
) : Component() {

    override fun body(): View = VStack(alignment = HorizontalAlignment.LEADING, spacing = 14f) {
        Text("Now Playing")
            .fontSize(18f)
            .fontWeight(FontWeight.BOLD)
            .accessibilityRole(Roles.HEADER)

        Image(computed { MusicPlayerView.at(trackIndex.get()).cover })
            .frame(width = 220f, height = 220f)
            .scaledToFill()
            .border {
                color(JColor.BLACK)
                width(1f)
                radius(8f)
            }

        Text(computed { MusicPlayerView.at(trackIndex.get()).title })
            .fontSize(20f)
            .fontWeight(FontWeight.BOLD)

        Text(computed {
            val track = MusicPlayerView.at(trackIndex.get())
            "${track.artist} · ${track.album}"
        }).foregroundStyle(MusicPlayerView.SECONDARY_FG)

        Divider().padding(top = 0f, right = 0f, bottom = 10f, left = 0f)

        Text("Up Next").fontWeight(FontWeight.SEMIBOLD)
        upNextRow(1)
        upNextRow(2)

        if (adUrl != null) {
            ProxyView(endpoint = adUrl, route = "/")
                .frame(width = 220f, height = 140f)
        }
    }.padding(24f)
        .background(MusicPlayerView.SIDEBAR_BG)
        .frame(width = MusicPlayerView.SIDEBAR_WIDTH, height = Commands.Size.FILL)

    /** The track [offset] places after the current one. */
    private fun upNextRow(offset: Int): View = Text(computed {
        val track = MusicPlayerView.at(trackIndex.get() + offset)
        (if (offset == 1) "Up next · " else "Then · ") + track.title + " — " + track.artist
    }).fontSize(13f)
        .foregroundStyle(MusicPlayerView.SECONDARY_FG)
}
