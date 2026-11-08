package dev.pathland.core.transport;

import dev.pathland.core.protocol.Commands;

import java.util.Objects;

/** A raw input event flowing host to guest through the EVENT opcode. */
public final class Event {
    private final int command;
    private final int target;
    private final int flags;
    private final float x;
    private final float y;
    private final float value;
    private final String text;
    private final int b;
    private final int c;

    public Event(
            int command,
            int target,
            int flags,
            float x,
            float y,
            float value,
            String text,
            int b,
            int c) {
        this.command = command;
        this.target = target;
        this.flags = flags;
        this.x = x;
        this.y = y;
        this.value = value;
        this.text = text;
        this.b = b;
        this.c = c;
    }

    public int command() {
        return command;
    }

    public int target() {
        return target;
    }

    public int flags() {
        return flags;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float value() {
        return value;
    }

    public String text() {
        return text;
    }

    public int b() {
        return b;
    }

    public int c() {
        return c;
    }

    public int getCommand() {
        return command;
    }

    public int getTarget() {
        return target;
    }

    public int getFlags() {
        return flags;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getValue() {
        return value;
    }

    public String getText() {
        return text;
    }

    public int getB() {
        return b;
    }

    public int getC() {
        return c;
    }

    public boolean isPointerDown() {
        return command == Commands.Event.POINTER_DOWN;
    }

    public boolean isPointerMove() {
        return command == Commands.Event.POINTER_MOVE;
    }

    public boolean isPointerUp() {
        return command == Commands.Event.POINTER_UP;
    }

    public boolean isKeyDown() {
        return command == Commands.Event.KEY_DOWN;
    }

    public boolean isKeyUp() {
        return command == Commands.Event.KEY_UP;
    }

    public boolean isValueChanged() {
        return command == Commands.Event.VALUE_CHANGED;
    }

    public boolean isTextChanged() {
        return command == Commands.Event.TEXT_CHANGED;
    }

    public boolean isFocusChanged() {
        return command == Commands.Event.FOCUS_CHANGED;
    }

    public boolean isEditingChanged() {
        return command == Commands.Event.EDITING_CHANGED;
    }

    public boolean isSubmit() {
        return command == Commands.Event.SUBMIT;
    }

    public boolean isScroll() {
        return command == Commands.Event.SCROLL;
    }

    public boolean isWheel() {
        return command == Commands.Event.WHEEL;
    }

    public boolean isDateChanged() {
        return command == Commands.Event.DATE_CHANGED;
    }

    public boolean isNavigate() {
        return command == Commands.Event.NAVIGATE;
    }

    public boolean isMediaPlayStateChanged() {
        return command == Commands.Event.MEDIA_PLAY_STATE_CHANGED;
    }

    public boolean isMediaTimeUpdated() {
        return command == Commands.Event.MEDIA_TIME_UPDATED;
    }

    public boolean isMediaEnded() {
        return command == Commands.Event.MEDIA_ENDED;
    }

    public boolean isMediaVolumeChanged() {
        return command == Commands.Event.MEDIA_VOLUME_CHANGED;
    }

    public boolean isMediaDurationChanged() {
        return command == Commands.Event.MEDIA_DURATION_CHANGED;
    }

    public boolean isFitChanged() {
        return command == Commands.Event.FIT_CHANGED;
    }

    public boolean mediaPlaying() {
        return b != 0;
    }

    public float mediaValue() {
        return value;
    }

    public int fitIndex() {
        return Math.round(value);
    }

    public String url() {
        return text;
    }

    public boolean isNavigateBack() {
        return isNavigate() && (flags & Commands.Flags.NAVIGATE_URL) == 0;
    }

    public int keyCode() {
        return b;
    }

    public int modifiers() {
        return c;
    }

    public int days() {
        return b;
    }

    public int millisOfDay() {
        return c;
    }

    public static Event pointerDown(int target, float x, float y) {
        return new Event(Commands.Event.POINTER_DOWN, target, 0, x, y, 0f, null, 0, 0);
    }

    public static Event pointerUp(int target, float x, float y) {
        return new Event(Commands.Event.POINTER_UP, target, 0, x, y, 0f, null, 0, 0);
    }

    public static Event pointerMove(int target, float x, float y, int flags) {
        return new Event(Commands.Event.POINTER_MOVE, target, flags, x, y, 0f, null, 0, 0);
    }

    public static Event keyDown(int target, int keyCode, int modifiers, int flags) {
        return new Event(Commands.Event.KEY_DOWN, target, flags, 0f, 0f, 0f, null, keyCode, modifiers);
    }

    public static Event keyUp(int target, int keyCode, int modifiers) {
        return new Event(Commands.Event.KEY_UP, target, 0, 0f, 0f, 0f, null, keyCode, modifiers);
    }

    public static Event valueChanged(int target, float value) {
        return new Event(Commands.Event.VALUE_CHANGED, target, 0, 0f, 0f, value, null, 0, 0);
    }

    public static Event textChanged(int target, String text) {
        return new Event(Commands.Event.TEXT_CHANGED, target, 0, 0f, 0f, 0f, text, 0, 0);
    }

    public static Event focusChanged(int target, boolean focused) {
        return new Event(Commands.Event.FOCUS_CHANGED, target, 0, 0f, 0f, 0f, null,
                focused ? 1 : 0, 0);
    }

    public static Event editingChanged(int target, boolean editing) {
        return new Event(Commands.Event.EDITING_CHANGED, target, 0, 0f, 0f, 0f, null,
                editing ? 1 : 0, 0);
    }

    public static Event submit(int target) {
        return new Event(Commands.Event.SUBMIT, target, 0, 0f, 0f, 0f, null, 0, 0);
    }

    public static Event scroll(int target, float offsetX, float offsetY) {
        return new Event(Commands.Event.SCROLL, target, 0, offsetX, offsetY, 0f, null, 0, 0);
    }

    public static Event wheel(int target, float deltaX, float deltaY) {
        return new Event(Commands.Event.WHEEL, target, 0, deltaX, deltaY, 0f, null, 0, 0);
    }

    public static Event dateChanged(int target, int days, int millisOfDay) {
        return new Event(Commands.Event.DATE_CHANGED, target, 0, 0f, 0f, 0f, null, days, millisOfDay);
    }

    public static Event navigate(String url) {
        return new Event(Commands.Event.NAVIGATE, 0, Commands.Flags.NAVIGATE_URL,
                0f, 0f, 0f, url, 0, 0);
    }

    public static Event navigateBack() {
        return new Event(Commands.Event.NAVIGATE, 0, 0, 0f, 0f, 0f, null, 0, 0);
    }

    public static Event mediaPlayStateChanged(int target, boolean playing) {
        return new Event(Commands.Event.MEDIA_PLAY_STATE_CHANGED, target, 0, 0f, 0f, 0f, null,
                playing ? 1 : 0, 0);
    }

    public static Event mediaTimeUpdated(int target, float seconds) {
        return new Event(Commands.Event.MEDIA_TIME_UPDATED, target, 0, 0f, 0f, seconds, null, 0, 0);
    }

    public static Event mediaEnded(int target) {
        return new Event(Commands.Event.MEDIA_ENDED, target, 0, 0f, 0f, 0f, null, 0, 0);
    }

    public static Event mediaVolumeChanged(int target, float volume) {
        return new Event(Commands.Event.MEDIA_VOLUME_CHANGED, target, 0, 0f, 0f, volume, null, 0, 0);
    }

    public static Event mediaDurationChanged(int target, float seconds) {
        return new Event(Commands.Event.MEDIA_DURATION_CHANGED, target, 0, 0f, 0f, seconds, null, 0, 0);
    }

    public static Event fitChanged(int target, float index) {
        return new Event(Commands.Event.FIT_CHANGED, target, 0, 0f, 0f, index, null, 0, 0);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Event)) {
            return false;
        }
        Event that = (Event) other;
        return command == that.command
                && target == that.target
                && flags == that.flags
                && Float.compare(x, that.x) == 0
                && Float.compare(y, that.y) == 0
                && Float.compare(value, that.value) == 0
                && Objects.equals(text, that.text)
                && b == that.b
                && c == that.c;
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, target, flags, x, y, value, text, b, c);
    }

    @Override
    public String toString() {
        return "Event(command=" + command + ", target=" + target + ", flags=" + flags
                + ", x=" + x + ", y=" + y + ", value=" + value + ", text=" + text
                + ", b=" + b + ", c=" + c + ")";
    }
}
