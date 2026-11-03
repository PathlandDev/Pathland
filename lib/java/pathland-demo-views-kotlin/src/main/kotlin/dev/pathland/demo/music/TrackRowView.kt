package dev.pathland.demo.music

import dev.pathland.view.core.Color as JColor
import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.core.signal.WritableSignal
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
 * Selecting it starts that track through the app-owned player state, and the
 * current row is highlighted reactively.
 */
internal class TrackRowView(
    private val track: Track,
    private val index: Int,
    private val trackIndex: WritableSignal<Int>,
    private val position: WritableSignal<Float>,
    private val playing: WritableSignal<Boolean>,
    private val seekRequest: WritableSignal<Float>,
) : Component() {

    override fun body(): View {
        val isCurrent = computed { MusicPlayerView.at(trackIndex.get()) === track }
        val bg = computed { if (isCurrent.get()) MusicPlayerView.ACTIVE_ROW_BG else JColor.CLEAR }
        val fg = computed { if (isCurrent.get()) MusicPlayerView.ACTIVE_ROW_FG else JColor.BLACK }

        return Button(action = {
            trackIndex.set(index)
            position.set(0f)
            seekRequest.set(0f)
            playing.set(true)
        }) {
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

                Text(MusicPlayerView.fmt(track.duration))
                    .fontSize(13f)
                    .foregroundStyle(MusicPlayerView.SECONDARY_FG)
            }
        }.background(bg)
            .foregroundStyle(fg)
            .padding(6f)
            .frame { width(Commands.Size.FILL) }
    }
}
