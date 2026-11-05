package dev.pathland.demo.music.sidebar

import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.demo.music.service.mediaPlayerService
import dev.pathland.view.core.View
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.foregroundStyle

/** One "Up next" / "Then" track line in the now-playing sidebar. */
internal class UpNextRowView(private val offset: Int) : Component() {

    override fun body(): View {
        val service = mediaPlayerService()

        return Text(computed {
            val track = service.trackAt(offset)
            (if (offset == 1) "Up next · " else "Then · ") + track.title + " — " + track.artist
        }).fontSize(13f)
            .foregroundStyle(MusicPlayerView.SECONDARY_FG)
    }
}
