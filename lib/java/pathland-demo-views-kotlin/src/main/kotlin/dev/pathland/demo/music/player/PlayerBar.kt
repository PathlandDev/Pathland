package dev.pathland.demo.music.player

import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.view.core.View
import dev.pathland.core.protocol.Commands
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.background
import dev.pathland.view.dsl.frame

/**
 * The bottom player bar: the [PlayerControls] surface over the shared player
 * service. Audio has no view — this bar is an ordinary view whose controls bind
 * the service's transport signals.
 */
internal class PlayerBar(
    private val wide: Boolean,
) : Component() {

    override fun body(): View = PlayerControls(wide)
        .background(MusicPlayerView.BAR_BG)
        .frame {
            width(Commands.Size.FILL)
            maxWidth(768f)
            height(MusicPlayerView.BAR_HEIGHT)
        }
}
