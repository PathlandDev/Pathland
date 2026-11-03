package dev.pathland.demo.music

import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.IconName
import dev.pathland.view.core.MediaController
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.dsl.Button
import dev.pathland.view.dsl.HStack
import dev.pathland.view.dsl.Icon
import dev.pathland.view.dsl.Image
import dev.pathland.view.dsl.KotlinView
import dev.pathland.view.dsl.Slider
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.unaryPlus
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.accessibilityLabel
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.fontWeight
import dev.pathland.view.dsl.foregroundStyle
import dev.pathland.view.dsl.frame
import dev.pathland.view.dsl.lineLimit
import dev.pathland.view.dsl.padding
import dev.pathland.view.dsl.scaledToFit

/**
 * The player bar's control surface: a centered row of
 * `[rewind] [play-pause] [forward] | [cover art] [track title] | [volume]`
 * over a slim, draggable seek bar that reports progress as a percent.
 */
internal class PlayerControls(
    private val player: MediaController,
    private val trackIndex: WritableSignal<Int>,
    private val showVolume: Boolean,
) : KotlinView() {

    override fun body(): View {
        val current = computed { MusicPlayerView.at(trackIndex.get()) }
        val transportIcon = computed {
            if (player.playing().get()) IconName.PAUSE.wire() else IconName.PLAY.wire()
        }
        val seek = SeekControl(player.time(), player.position())
        val volumeControl = VolumeControl(player.volumeReport(), player.volume())

        fun playControl(): View = HStack(alignment = VerticalAlignment.CENTER, spacing = 18f) {
            Button(action = { prev() }) {
                Icon(IconName.SKIP_BACK)
            }.fontSize(20f)
                .accessibilityLabel("Previous track")

            Button(action = { player.toggle() }) {
                Icon(transportIcon)
            }.fontSize(20f)
                .accessibilityLabel(computed { if (player.playing().get()) "Pause" else "Play" })

            Button(action = { next() }) {
                Icon(IconName.SKIP_FORWARD)
            }.fontSize(20f)
                .accessibilityLabel("Next track")
        }

        fun trackInfo(): View = HStack(alignment = VerticalAlignment.CENTER, spacing = 18f) {
            Image(computed { current.get().cover })
                .frame(width = 36f, height = 36f)
                .scaledToFit()

            VStack(alignment = HorizontalAlignment.LEADING, spacing = 2f) {
                Text(computed { current.get().title })
                    .fontWeight(FontWeight.SEMIBOLD)
                    .lineLimit(1)
                Text(computed { current.get().artist })
                    .fontSize(13f)
                    .foregroundStyle(MusicPlayerView.SECONDARY_FG)
                    .lineLimit(1)
            }.frame(width = 160f)
        }

        fun volumeView(): View = HStack(alignment = VerticalAlignment.CENTER, spacing = 18f) {
            Icon(IconName.VOLUME).fontSize(16f)
            Slider(volumeControl, 0f..1f)
                .accessibilityLabel("Volume")
        }

        return VStack(alignment = HorizontalAlignment.CENTER, spacing = 0f) {
            HStack(alignment = VerticalAlignment.CENTER, spacing = 18f) {
                playControl()
                Text("|").fontSize(18f).foregroundStyle(MusicPlayerView.SECONDARY_FG)
                trackInfo()
                if (showVolume) {
                    Text("|").fontSize(18f).foregroundStyle(MusicPlayerView.SECONDARY_FG)
                    volumeView()
                }
            }
            Slider(
                PercentSeek(seek) { MusicPlayerView.at(trackIndex.get()).duration },
                0f..100f,
                seek::onEditingChanged,
            ).frame { width(Float.POSITIVE_INFINITY) }
                .padding(top = 0f, right = 16f, bottom = 0f, left = 16f)
                .accessibilityLabel("Seek")
        }
    }

    /** Previous track (wraps around), restarting from the top. */
    private fun prev() {
        trackIndex.set((trackIndex.get() - 1 + MusicPlayerView.TRACKS.size) % MusicPlayerView.TRACKS.size)
        player.seek(0f)
    }

    /** Next track (wraps around), restarting from the top. */
    private fun next() {
        trackIndex.set((trackIndex.get() + 1) % MusicPlayerView.TRACKS.size)
        player.seek(0f)
    }
}
