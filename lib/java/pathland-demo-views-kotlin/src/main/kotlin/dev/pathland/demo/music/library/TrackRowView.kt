package dev.pathland.demo.music.library

import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.demo.music.model.Track
import dev.pathland.demo.music.service.MediaPlayerService
import dev.pathland.demo.music.service.mediaPlayerService
import dev.pathland.view.core.Color
import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.dsl.Button
import dev.pathland.view.dsl.HStack
import dev.pathland.view.dsl.Image
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.Spacer
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.background
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.fontWeight
import dev.pathland.view.dsl.foregroundStyle
import dev.pathland.view.dsl.frame
import dev.pathland.view.dsl.padding
import dev.pathland.view.dsl.scaledToFit

/**
 * A single track row: a cover thumbnail, the title/artist, and the duration.
 * Selecting it starts that track through the shared player service, and the
 * current row is highlighted reactively.
 */
internal class TrackRowView(
    private val track: Track,
    private val index: Int,
) : Component() {

    override fun body(): View {
        val service = mediaPlayerService()
        val isCurrent = computed { service.currentTrack.get() === track }
        val bg = computed { if (isCurrent.get()) MusicPlayerView.ACTIVE_ROW_BG else Color.CLEAR }
        val fg = computed { if (isCurrent.get()) MusicPlayerView.ACTIVE_ROW_FG else Color.BLACK }

        return Button(action = { service.selectTrack(index) }) {
            HStack(alignment = VerticalAlignment.CENTER, spacing = 12f) {
                Image(track.cover)
                    .frame(width = 44f, height = 44f)
                    .scaledToFit()

                VStack(alignment = HorizontalAlignment.LEADING, spacing = 2f) {
                    Text(track.title).fontWeight(FontWeight.SEMIBOLD)
                    Text("${track.artist} · ${track.album}")
                        .fontSize(13f)
                        .foregroundStyle(MusicPlayerView.SECONDARY_FG)
                }

                Spacer()

                Text(MediaPlayerService.fmt(track.duration))
                    .fontSize(13f)
                    .foregroundStyle(MusicPlayerView.SECONDARY_FG)
            }
        }.background(bg)
            .foregroundStyle(fg)
            .padding(6f)
            .frame { width(Commands.Size.FILL) }
    }
}
