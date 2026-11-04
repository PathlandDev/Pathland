package dev.pathland.demo.music.player

import dev.pathland.demo.music.player.controls.VolumeControl
import dev.pathland.demo.music.service.mediaPlayerService
import dev.pathland.view.core.IconName
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.HStack
import dev.pathland.view.dsl.Icon
import dev.pathland.view.dsl.Slider
import dev.pathland.view.dsl.accessibilityLabel
import dev.pathland.view.dsl.fontSize

/** The volume icon and 0..1 slider in the player bar. */
internal class VolumeControls : Component() {

    override fun body(): View {
        val service = mediaPlayerService()
        val volume = VolumeControl(service.volume, service.volumeRequest)

        return HStack(alignment = VerticalAlignment.CENTER, spacing = 18f) {
            Icon(IconName.VOLUME).fontSize(16f)
            Slider(volume, 0f..1f)
                .accessibilityLabel("Volume")
        }
    }
}
