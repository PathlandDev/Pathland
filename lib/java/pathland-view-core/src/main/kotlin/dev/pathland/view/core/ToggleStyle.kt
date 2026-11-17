package dev.pathland.view.core

import dev.pathland.core.protocol.WireValue

/**
 * The visual form of a `TOGGLE` (`TOGGLE_STYLE`): switch=0 (the default),
 * checkbox=1, or a pressable button=2. The renderer maps it to its native
 * control while the app keeps the boolean value binding.
 */
enum class ToggleStyle(private val wire: Int) : WireValue {
    SWITCH(0),
    CHECKBOX(1),
    BUTTON(2);

    override fun wire(): Int = wire
}
