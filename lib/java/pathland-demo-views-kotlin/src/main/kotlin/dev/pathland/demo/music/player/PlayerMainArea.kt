package dev.pathland.demo.music.player

import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.demo.music.library.LibraryView
import dev.pathland.demo.music.lyrics.SubtitlePill
import dev.pathland.view.core.Alignment
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.HStack
import dev.pathland.view.dsl.Spacer
import dev.pathland.view.dsl.ZStack
import dev.pathland.view.dsl.border
import dev.pathland.view.dsl.padding
import dev.pathland.view.dsl.shadow

/**
 * The library plus the floating player bar and lyric pill. The root fit slot
 * instantiates this once per candidate; [showVolume] selects the bar controls.
 */
internal class PlayerMainArea(
    private val showVolume: Boolean,
) : Component() {

    override fun body(): View =
        ZStack(alignment = Alignment.BOTTOM_CENTER) {
            LibraryView()
            HStack {
                Spacer()
                PlayerBar(showVolume)
                    .border(MusicPlayerView.BAR_BORDER, 1f, 500f)
                    .shadow(10f)
                Spacer()
            }.padding(16f)
            SubtitlePill()
        }
}
