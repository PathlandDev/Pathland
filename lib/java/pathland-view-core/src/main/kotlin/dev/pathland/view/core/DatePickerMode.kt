package dev.pathland.view.core

import dev.pathland.core.protocol.WireValue

/**
 * Which components a `DATE_PICKER` edits (`DATE_PICKER_MODE`): date=0, time=1,
 * or date and time=2. The value itself rides `SET_DATE`/`DATE_CHANGED`
 * (days-since-epoch + millis-of-day).
 */
enum class DatePickerMode(private val wire: Int) : WireValue {
    DATE(0),
    TIME(1),
    DATE_AND_TIME(2);

    override fun wire(): Int = wire
}
