package dev.pathland.demo.music

import dev.pathland.view.core.MediaController
import dev.pathland.view.core.View
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.dsl.KotlinView
import dev.pathland.view.dsl.background
import dev.pathland.view.dsl.frame

/**
 * The bottom player bar: the [PlayerControls] surface over the player's
 * [MediaController]. Audio has no view — this bar is an ordinary view whose
 * controls bind the controller's transport signals.
 */
internal class PlayerBar(
    private val trackIndex: WritableSignal<Int>,
    private val player: MediaController,
) : KotlinView() {

    override fun body(): View = PlayerControls(player, trackIndex)
        .background(MusicPlayerView.BAR_BG)
        .frame {
            width(Commands.Size.FILL)
            maxWidth(768f)
            height(MusicPlayerView.BAR_HEIGHT)
        }
}
