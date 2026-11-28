package dev.pathland.demo.music.player

import dev.pathland.demo.music.service.mediaPlayerService
import dev.pathland.view.core.IconName
import dev.pathland.view.core.ThemeMode
import dev.pathland.view.core.View
import dev.pathland.view.dsl.Button
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.Icon
import dev.pathland.view.dsl.accessibilityLabel
import dev.pathland.view.dsl.fontSize

/**
 * A button that cycles the app's color-scheme preference:
 * system → light → dark → system.
 *
 * The host observes the shared [dev.pathland.demo.music.service.MediaPlayerService.colorScheme]
 * signal and emits `META::SET_COLOR_SCHEME`; the icon and accessible label follow
 * the current mode.
 */
internal class ThemeModeButton : Component() {

    override fun body(): View {
        val service = mediaPlayerService()
        val mode = service.colorScheme.get()
        val icon = when (mode) {
            ThemeMode.PLATFORM -> IconName.SETTINGS
            ThemeMode.LIGHT -> IconName.SUN
            ThemeMode.DARK -> IconName.MOON
        }
        val label = "Color mode: " + when (mode) {
            ThemeMode.PLATFORM -> "System"
            ThemeMode.LIGHT -> "Light"
            ThemeMode.DARK -> "Dark"
        }

        return Button(action = { service.cycleColorScheme() }) {
            Icon(icon).fontSize(16f)
        }.accessibilityLabel(label)
    }
}
