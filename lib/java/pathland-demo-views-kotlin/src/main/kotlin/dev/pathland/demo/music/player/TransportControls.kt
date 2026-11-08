package dev.pathland.demo.music.player

import dev.pathland.demo.music.service.mediaPlayerService
import dev.pathland.view.core.IconName
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.dsl.Button
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.HStack
import dev.pathland.view.dsl.Icon
import dev.pathland.view.dsl.accessibilityLabel
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.unaryPlus

/** The previous / play-pause / next transport buttons. */
internal class TransportControls(
    private val wide: Boolean
) : Component() {

    override fun body(): View {
        val service = mediaPlayerService()
        val transportIcon = computed {
            if (service.controller.playing().get()) IconName.PAUSE.wire() else IconName.PLAY.wire()
        }

        val backButton = Button(action = { service.previous() }) {
                Icon(IconName.SKIP_BACK)
            }.fontSize(20f)
            .accessibilityLabel("Previous track")

        val playButton = Button(action = { service.togglePlay() }) {
            Icon(transportIcon)
            }.fontSize(20f)
            .accessibilityLabel(computed {
                if (service.controller.playing().get()) "Pause" else "Play"
            })

        val forwardButton = Button(action = { service.next() }) {
                Icon(IconName.SKIP_FORWARD)
            }.fontSize(20f)
            .accessibilityLabel("Next track")

        return if(!wide) HStack {
            +playButton
        } else HStack(alignment = VerticalAlignment.CENTER, spacing = 18f) {
            +backButton
            +playButton
            +forwardButton
        }

    }
}
