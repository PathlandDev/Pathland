package dev.pathland.demo.music.sidebar

import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.demo.music.service.mediaPlayerService
import dev.pathland.view.core.Color
import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.Roles
import dev.pathland.view.core.View
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.Divider
import dev.pathland.view.dsl.Image
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
    private val adUrl: String?,
) : Component() {

    override fun body(): View {
        val service = mediaPlayerService()

        return VStack(alignment = HorizontalAlignment.LEADING, spacing = 14f) {
            Text("Now Playing")
                .fontSize(18f)
                .fontWeight(FontWeight.BOLD)
                .accessibilityRole(Roles.HEADER)

            Image(computed { service.currentTrack.get().cover })
                .frame(width = 220f, height = 220f)
                .scaledToFill()
                .border {
                    color(Color.BLACK)
                    width(1f)
                    radius(8f)
                }

            Text(computed { service.currentTrack.get().title })
                .fontSize(20f)
                .fontWeight(FontWeight.BOLD)

            Text(computed {
                val track = service.currentTrack.get()
                "${track.artist} · ${track.album}"
            }).foregroundStyle(MusicPlayerView.SECONDARY_FG)

            Divider().padding(top = 0f, right = 0f, bottom = 10f, left = 0f)

            Text("Up Next").fontWeight(FontWeight.SEMIBOLD)
            UpNextRowView(1)
            UpNextRowView(2)

            if (adUrl != null) {
                ProxyView(endpoint = adUrl, route = "/")
                    .frame(width = 220f, height = 140f)
            }
        }.padding(24f)
            .background(MusicPlayerView.SIDEBAR_BG)
            .frame(width = MusicPlayerView.SIDEBAR_WIDTH, height = Commands.Size.FILL)
    }
}
