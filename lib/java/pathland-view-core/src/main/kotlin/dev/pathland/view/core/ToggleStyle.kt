package dev.pathland.view.core

import dev.pathland.view.core.protocol.WireValue

/** The ToggleStyle wire enum. */
enum class ToggleStyle(private val wire: Int) : WireValue {
    SWITCH(0),
    CHECKBOX(1),
    BUTTON(2);

    override fun wire(): Int = wire
}
