package dev.pathland.demo.music.library

import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.demo.music.model.Track
import dev.pathland.demo.music.service.MediaPlayerService
import dev.pathland.demo.music.service.mediaPlayerService
import dev.pathland.view.core.Color
import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.IconName
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.core.View
import dev.pathland.core.protocol.Commands
import dev.pathland.view.core.TextAlignment
import dev.pathland.view.core.Roles
import dev.pathland.view.dsl.HStack
import dev.pathland.view.dsl.Icon
import dev.pathland.view.dsl.Image
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.Spacer
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.accessibilityRole
import dev.pathland.view.dsl.background
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.fontWeight
import dev.pathland.view.dsl.foregroundStyle
import dev.pathland.view.dsl.frame
import dev.pathland.view.dsl.lineLimit
import dev.pathland.view.dsl.multilineTextAlignment
import dev.pathland.view.dsl.opacity
import dev.pathland.view.dsl.padding
import dev.pathland.view.dsl.scaledToFit

/**
 * A single track row: a playing indicator gutter, cover thumbnail, title/artist,
 * and duration. The selected row is highlighted; the current track's fixed
 * speaker icon is revealed by opacity. Both states are independent and
 * reactive.
 */
internal class TrackRowView(
    private val track: Track,
    private val index: Int,
) : Component() {

    override fun body(): View {
        val service = mediaPlayerService()
        val isCurrent = computed { service.trackIndex.get() == index }
        val isSelected = computed { service.focusedIndex.get() == index }
        val isListFocused = computed { service.listFocused.get() }
        val bg = computed {
            when {
                !isSelected.get() -> Color.CLEAR
                isListFocused.get() -> MusicPlayerView.ACTIVE_ROW_BG
                else -> MusicPlayerView.INACTIVE_ROW_BG
            }
        }
        val fg = computed {
            if (isSelected.get() && isListFocused.get()) MusicPlayerView.ACTIVE_ROW_FG else Color.BLACK
        }
        val playingIconOpacity = computed { if (isCurrent.get()) 1f else 0f }

        return HStack(alignment = VerticalAlignment.CENTER, spacing = 12f) {
            // Keep this child stable: only its opacity changes, so a previous
            // playing track can never leave a stray icon behind.
            Icon(IconName.VOLUME)
                .fontSize(16f)
                .foregroundStyle(MusicPlayerView.ACTIVE_ROW_FG)
                .frame(width = 18f, height = 18f)
                .opacity(playingIconOpacity)

            Image(track.thumbnail)
                .frame(width = 44f, height = 44f)
                .scaledToFit()

            VStack(alignment = HorizontalAlignment.LEADING, spacing = 2f) {
                Text(track.title)
                    .fontWeight(FontWeight.SEMIBOLD)
                    .multilineTextAlignment(TextAlignment.LEADING)
                    .lineLimit(1)

                Text("${track.artist} · ${track.album}")
                    .fontSize(13f)
                    .multilineTextAlignment(TextAlignment.LEADING)
                    .lineLimit(1)
                    .foregroundStyle(MusicPlayerView.SECONDARY_FG)
            }

            Spacer()

            Text(MediaPlayerService.fmt(track.duration))
                .fontSize(13f)
                .foregroundStyle(MusicPlayerView.SECONDARY_FG)
        }
            .accessibilityRole(Roles.LIST_ITEM)
            .background(bg)
            .foregroundStyle(fg)
            .padding(6f)
            .frame { width(Commands.Size.FILL) }
    }
}
