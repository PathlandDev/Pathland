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

/** The previous / play-pause / next transport buttons. */
internal class TransportControls : Component() {

    override fun body(): View {
        val service = mediaPlayerService()
        val transportIcon = computed {
            if (service.controller.playing().get()) IconName.PAUSE.wire() else IconName.PLAY.wire()
        }

        return HStack(alignment = VerticalAlignment.CENTER, spacing = 18f) {
            Button(action = { service.previous() }) {
                Icon(IconName.SKIP_BACK)
            }.fontSize(20f)
                .accessibilityLabel("Previous track")

            Button(action = { service.togglePlay() }) {
                Icon(transportIcon)
            }.fontSize(20f)
                .accessibilityLabel(computed {
                    if (service.controller.playing().get()) "Pause" else "Play"
                })

            Button(action = { service.next() }) {
                Icon(IconName.SKIP_FORWARD)
            }.fontSize(20f)
                .accessibilityLabel("Next track")
        }
    }
}
