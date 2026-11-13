package dev.pathland.demo.music.library

import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.demo.music.service.mediaPlayerService
import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.Roles
import dev.pathland.view.core.View
import dev.pathland.core.protocol.Commands
import dev.pathland.view.dsl.Divider
import dev.pathland.view.dsl.ForEach
import dev.pathland.view.dsl.Font
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.LazyVStack
import dev.pathland.view.dsl.ScrollView
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.accessibilityRole
import dev.pathland.view.dsl.font
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.fontWeight
import dev.pathland.view.dsl.foregroundStyle
import dev.pathland.view.dsl.frame
import dev.pathland.view.dsl.padding

/** The fixed main-axis height of a library row (the lazy window's extent). */
internal const val LIBRARY_ROW_HEIGHT = 64f

/**
 * The library pane: the "Library" header and the full track list. Each row is a
 * [TrackRowView]; selecting one starts that track through the shared player
 * service. The list is a windowed `LazyVStack` over the repeated library.
 */
internal class LibraryView : Component() {

    override fun body(): View {
        val service = mediaPlayerService()

        return VStack {
            Text("Library")
                .font(Font.largeTitle())
                .fontSize(26f)
                .fontWeight(FontWeight.BOLD)
                .accessibilityRole(Roles.HEADER)

            Text("${service.tracks.size} songs · ${service.tracks.size} albums")
                .foregroundStyle(MusicPlayerView.SECONDARY_FG)

            Divider().padding(top = 0f, right = 0f, bottom = 10f, left = 0f)

            ScrollView {
                LazyVStack(spacing = 0f) {
                    ForEach(service.tracks) { track, index ->
                        TrackRowView(track, index).frame(height = LIBRARY_ROW_HEIGHT)
                    }
                }
            }
        }.padding(24f)
            .frame(width = Commands.Size.FILL, height = Commands.Size.FILL)
    }
}
