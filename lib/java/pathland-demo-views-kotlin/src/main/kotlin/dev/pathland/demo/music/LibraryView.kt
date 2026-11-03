package dev.pathland.demo.music

import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.Roles
import dev.pathland.view.core.View
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.dsl.Divider
import dev.pathland.view.dsl.Font
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.ScrollView
import dev.pathland.view.dsl.Spacer
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.accessibilityRole
import dev.pathland.view.dsl.font
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.fontWeight
import dev.pathland.view.dsl.foregroundStyle
import dev.pathland.view.dsl.frame
import dev.pathland.view.dsl.padding

/**
 * The library pane: the "Library" header and the full track list. Each row is a
 * [TrackRowView]; selecting one starts that track through the app-owned player
 * state (owned by the root view).
 */
internal class LibraryView(
    private val trackIndex: WritableSignal<Int>,
    private val position: WritableSignal<Float>,
    private val playing: WritableSignal<Boolean>,
    private val seekRequest: WritableSignal<Float>,
) : Component() {

    override fun body(): View = VStack {
        Text("Library")
            .font(Font.largeTitle())
            .fontSize(26f)
            .fontWeight(FontWeight.BOLD)
            .accessibilityRole(Roles.HEADER)

        Text("${MusicPlayerView.TRACKS.size} songs · ${MusicPlayerView.TRACKS.size} albums")
            .foregroundStyle(MusicPlayerView.SECONDARY_FG)

        Divider().padding(top = 0f, right = 0f, bottom = 10f, left = 0f)

        ScrollView {
            VStack {
                MusicPlayerView.TRACKS.forEachIndexed { index, track ->
                    TrackRowView(track, index, trackIndex, position, playing, seekRequest)
                }
                Spacer()
            }
        }
    }.padding(24f)
        .frame(width = Commands.Size.FILL, height = Commands.Size.FILL)
}
