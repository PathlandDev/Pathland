package dev.pathland.demo.music.player

import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.demo.music.player.controls.PercentSeek
import dev.pathland.demo.music.player.controls.SeekControl
import dev.pathland.demo.music.service.mediaPlayerService
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.HStack
import dev.pathland.view.dsl.Slider
import dev.pathland.view.dsl.Spacer
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.accessibilityLabel
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.foregroundStyle
import dev.pathland.view.dsl.frame
import dev.pathland.view.dsl.padding

/**
 * The player bar's control surface: a centered row of
 * `[rewind] [play-pause] [forward] | [cover art] [track title] | [volume]`
 * over a slim, draggable seek bar that reports progress as a percent.
 */
internal class PlayerControls(
    private val wide: Boolean,
) : Component() {

    override fun body(): View {
        val service = mediaPlayerService()
        val seek = SeekControl(service.position, service.seekRequest)

        return VStack(alignment = HorizontalAlignment.CENTER, spacing = 0f) {
            HStack(alignment = VerticalAlignment.CENTER, spacing = 18f) {
                Spacer()
                TransportControls(wide)
                Text("|").fontSize(18f).foregroundStyle(MusicPlayerView.SECONDARY_FG)
                TrackInfoView()
                if (wide) {
                    Text("|").fontSize(18f).foregroundStyle(MusicPlayerView.SECONDARY_FG)
                    VolumeControls()
                }
                Spacer()
            }
            Slider(
                PercentSeek(seek) { service.currentTrack.get().duration },
                0f..100f,
                seek::onEditingChanged,
            ).frame { width(Float.POSITIVE_INFINITY) }
                .padding(top = 0f, right = 16f, bottom = 0f, left = 16f)
                .accessibilityLabel("Seek")
        }
    }
}
