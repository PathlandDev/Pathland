package dev.pathland.view.core

import dev.pathland.view.core.protocol.WireValue

/** The DatePickerMode wire enum. */
enum class DatePickerMode(private val wire: Int) : WireValue {
    DATE(0),
    TIME(1),
    DATE_AND_TIME(2);

    override fun wire(): Int = wire
}
