package dev.pathland.view.core.transport

import dev.pathland.view.core.protocol.Commands

/** A raw input event flowing host → guest through the EVENT opcode. */
data class Event(
    val command: Int,
    val target: Int,
    val flags: Int,
    val x: Float,
    val y: Float,
    val value: Float,
    val text: String?,
    val b: Int,
    val c: Int,
) {
    fun command(): Int = command
    fun target(): Int = target
    fun flags(): Int = flags
    fun x(): Float = x
    fun y(): Float = y
    fun value(): Float = value
    fun text(): String? = text
    fun b(): Int = b
    fun c(): Int = c

    val isPointerDown: Boolean
        get() = command == Commands.Event.POINTER_DOWN
    val isPointerMove: Boolean
        get() = command == Commands.Event.POINTER_MOVE
    val isPointerUp: Boolean
        get() = command == Commands.Event.POINTER_UP
    val isKeyDown: Boolean
        get() = command == Commands.Event.KEY_DOWN
    val isKeyUp: Boolean
        get() = command == Commands.Event.KEY_UP
    val isValueChanged: Boolean
        get() = command == Commands.Event.VALUE_CHANGED
    val isTextChanged: Boolean
        get() = command == Commands.Event.TEXT_CHANGED
    val isFocusChanged: Boolean
        get() = command == Commands.Event.FOCUS_CHANGED
    val isEditingChanged: Boolean
        get() = command == Commands.Event.EDITING_CHANGED
    val isSubmit: Boolean
        get() = command == Commands.Event.SUBMIT
    val isScroll: Boolean
        get() = command == Commands.Event.SCROLL
    val isWheel: Boolean
        get() = command == Commands.Event.WHEEL
    val isDateChanged: Boolean
        get() = command == Commands.Event.DATE_CHANGED
    val isNavigate: Boolean
        get() = command == Commands.Event.NAVIGATE
    val isMediaPlayStateChanged: Boolean
        get() = command == Commands.Event.MEDIA_PLAY_STATE_CHANGED
    val isMediaTimeUpdated: Boolean
        get() = command == Commands.Event.MEDIA_TIME_UPDATED
    val isMediaEnded: Boolean
        get() = command == Commands.Event.MEDIA_ENDED
    val isMediaVolumeChanged: Boolean
        get() = command == Commands.Event.MEDIA_VOLUME_CHANGED
    val isMediaDurationChanged: Boolean
        get() = command == Commands.Event.MEDIA_DURATION_CHANGED
    val isFitChanged: Boolean
        get() = command == Commands.Event.FIT_CHANGED

    fun mediaPlaying(): Boolean = b != 0
    fun mediaValue(): Float = value
    fun fitIndex(): Int = Math.round(value)
    fun url(): String? = text
    val isNavigateBack: Boolean
        get() = isNavigate && (flags and Commands.Flags.NAVIGATE_URL) == 0
    fun keyCode(): Int = b
    fun modifiers(): Int = c
    fun days(): Int = b
    fun millisOfDay(): Int = c

    companion object {
        @JvmStatic
        fun pointerDown(target: Int, x: Float, y: Float) =
            Event(Commands.Event.POINTER_DOWN, target, 0, x, y, 0f, null, 0, 0)

        @JvmStatic
        fun pointerUp(target: Int, x: Float, y: Float) =
            Event(Commands.Event.POINTER_UP, target, 0, x, y, 0f, null, 0, 0)

        @JvmStatic
        fun pointerMove(target: Int, x: Float, y: Float, flags: Int) =
            Event(Commands.Event.POINTER_MOVE, target, flags, x, y, 0f, null, 0, 0)

        @JvmStatic
        fun keyDown(target: Int, keyCode: Int, modifiers: Int, flags: Int) =
            Event(Commands.Event.KEY_DOWN, target, flags, 0f, 0f, 0f, null, keyCode, modifiers)

        @JvmStatic
        fun keyUp(target: Int, keyCode: Int, modifiers: Int) =
            Event(Commands.Event.KEY_UP, target, 0, 0f, 0f, 0f, null, keyCode, modifiers)

        @JvmStatic
        fun valueChanged(target: Int, value: Float) =
            Event(Commands.Event.VALUE_CHANGED, target, 0, 0f, 0f, value, null, 0, 0)

        @JvmStatic
        fun textChanged(target: Int, text: String?) =
            Event(Commands.Event.TEXT_CHANGED, target, 0, 0f, 0f, 0f, text, 0, 0)

        @JvmStatic
        fun focusChanged(target: Int, focused: Boolean) =
            Event(Commands.Event.FOCUS_CHANGED, target, 0, 0f, 0f, 0f, null, if (focused) 1 else 0, 0)

        @JvmStatic
        fun editingChanged(target: Int, editing: Boolean) =
            Event(Commands.Event.EDITING_CHANGED, target, 0, 0f, 0f, 0f, null, if (editing) 1 else 0, 0)

        @JvmStatic
        fun submit(target: Int) =
            Event(Commands.Event.SUBMIT, target, 0, 0f, 0f, 0f, null, 0, 0)

        @JvmStatic
        fun scroll(target: Int, offsetX: Float, offsetY: Float) =
            Event(Commands.Event.SCROLL, target, 0, offsetX, offsetY, 0f, null, 0, 0)

        @JvmStatic
        fun wheel(target: Int, deltaX: Float, deltaY: Float) =
            Event(Commands.Event.WHEEL, target, 0, deltaX, deltaY, 0f, null, 0, 0)

        @JvmStatic
        fun dateChanged(target: Int, days: Int, millisOfDay: Int) =
            Event(Commands.Event.DATE_CHANGED, target, 0, 0f, 0f, 0f, null, days, millisOfDay)

        @JvmStatic
        fun navigate(url: String?) =
            Event(Commands.Event.NAVIGATE, 0, Commands.Flags.NAVIGATE_URL, 0f, 0f, 0f, url, 0, 0)

        @JvmStatic
        fun navigateBack() =
            Event(Commands.Event.NAVIGATE, 0, 0, 0f, 0f, 0f, null, 0, 0)

        @JvmStatic
        fun mediaPlayStateChanged(target: Int, playing: Boolean) =
            Event(Commands.Event.MEDIA_PLAY_STATE_CHANGED, target, 0, 0f, 0f, 0f, null, if (playing) 1 else 0, 0)

        @JvmStatic
        fun mediaTimeUpdated(target: Int, seconds: Float) =
            Event(Commands.Event.MEDIA_TIME_UPDATED, target, 0, 0f, 0f, seconds, null, 0, 0)

        @JvmStatic
        fun mediaEnded(target: Int) =
            Event(Commands.Event.MEDIA_ENDED, target, 0, 0f, 0f, 0f, null, 0, 0)

        @JvmStatic
        fun mediaVolumeChanged(target: Int, volume: Float) =
            Event(Commands.Event.MEDIA_VOLUME_CHANGED, target, 0, 0f, 0f, volume, null, 0, 0)

        @JvmStatic
        fun mediaDurationChanged(target: Int, seconds: Float) =
            Event(Commands.Event.MEDIA_DURATION_CHANGED, target, 0, 0f, 0f, seconds, null, 0, 0)

        @JvmStatic
        fun fitChanged(target: Int, index: Float) =
            Event(Commands.Event.FIT_CHANGED, target, 0, 0f, 0f, index, null, 0, 0)
    }
}
