package dev.pathland.demo.music.player

import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.demo.music.service.mediaPlayerService
import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.HStack
import dev.pathland.view.dsl.Image
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.fontWeight
import dev.pathland.view.dsl.foregroundStyle
import dev.pathland.view.dsl.frame
import dev.pathland.view.dsl.lineLimit
import dev.pathland.view.dsl.scaledToFit

/** The current track's cover, title, and artist in the player bar. */
internal class TrackInfoView : Component() {

    override fun body(): View {
        val service = mediaPlayerService()
        val current = computed { service.currentTrack.get() }

        return HStack(alignment = VerticalAlignment.CENTER, spacing = 18f) {
            Image(computed { current.get().thumbnail })
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
    }
}
