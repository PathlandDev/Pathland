package com.pathland.view.transport;

import com.pathland.view.Commands;

/**
 * A raw input event flowing host → guest through the opcode engine (an {@code EVENT}
 * opcode).
 *
 * <p>Transport-dependent payload per kind (see {@code spec/EVENTS.md}):
 * <ul>
 *   <li>pointer ({@code x}/{@code y}) — viewport-relative logical points;</li>
 *   <li>{@code VALUE_CHANGED} ({@code value}) — the control's semantic value;</li>
 *   <li>key ({@code b}/{@code c}) — keyCode / modifier bitmask;</li>
 *   <li>{@code DATE_CHANGED} ({@code b}/{@code c}) — days since epoch / millis of day;</li>
 *   <li>{@code TEXT_CHANGED} ({@code text}) — carried in the batch's string section;</li>
 *   <li>{@code SCROLL}/{@code WHEEL} ({@code x}/{@code y}) — offsets/deltas.</li>
 * </ul>
 */
public record Event(
        int command, int target, int flags, float x, float y, float value, String text, int b, int c) {

    public static Event pointerDown(int target, float x, float y) {
        return new Event(Commands.Event.POINTER_DOWN, target, 0, x, y, 0, null, 0, 0);
    }

    public static Event pointerUp(int target, float x, float y) {
        return new Event(Commands.Event.POINTER_UP, target, 0, x, y, 0, null, 0, 0);
    }

    public static Event pointerMove(int target, float x, float y, int flags) {
        return new Event(Commands.Event.POINTER_MOVE, target, flags, x, y, 0, null, 0, 0);
    }

    public static Event keyDown(int target, int keyCode, int modifiers, int flags) {
        return new Event(Commands.Event.KEY_DOWN, target, flags, 0, 0, 0, null, keyCode, modifiers);
    }

    public static Event keyUp(int target, int keyCode, int modifiers) {
        return new Event(Commands.Event.KEY_UP, target, 0, 0, 0, 0, null, keyCode, modifiers);
    }

    public static Event valueChanged(int target, float value) {
        return new Event(Commands.Event.VALUE_CHANGED, target, 0, 0, 0, value, null, 0, 0);
    }

    public static Event textChanged(int target, String text) {
        return new Event(Commands.Event.TEXT_CHANGED, target, 0, 0, 0, 0, text, 0, 0);
    }

    public static Event focusChanged(int target, boolean focused) {
        return new Event(Commands.Event.FOCUS_CHANGED, target, 0, 0, 0, 0, null, focused ? 1 : 0, 0);
    }

    public static Event editingChanged(int target, boolean editing) {
        return new Event(Commands.Event.EDITING_CHANGED, target, 0, 0, 0, 0, null, editing ? 1 : 0, 0);
    }

    public static Event submit(int target) {
        return new Event(Commands.Event.SUBMIT, target, 0, 0, 0, 0, null, 0, 0);
    }

    public static Event scroll(int target, float offsetX, float offsetY) {
        return new Event(Commands.Event.SCROLL, target, 0, offsetX, offsetY, 0, null, 0, 0);
    }

    public static Event wheel(int target, float deltaX, float deltaY) {
        return new Event(Commands.Event.WHEEL, target, 0, deltaX, deltaY, 0, null, 0, 0);
    }

    public static Event dateChanged(int target, int days, int millisOfDay) {
        return new Event(Commands.Event.DATE_CHANGED, target, 0, 0, 0, 0, null, days, millisOfDay);
    }

    /** Global navigation to a URL (web back/forward/deep-link); the URL rides the string section. */
    public static Event navigate(String url) {
        return new Event(Commands.Event.NAVIGATE, 0, Commands.Flags.NAVIGATE_URL, 0, 0, 0, url, 0, 0);
    }

    /** Global native back request (no URL — "back one step"). */
    public static Event navigateBack() {
        return new Event(Commands.Event.NAVIGATE, 0, 0, 0, 0, 0, null, 0, 0);
    }

    /** A bound media node's playback state changed (spec/EVENTS.md Media). */
    public static Event mediaPlayStateChanged(int target, boolean playing) {
        return new Event(Commands.Event.MEDIA_PLAY_STATE_CHANGED, target, 0, 0, 0, 0, null, playing ? 1 : 0, 0);
    }

    /** A bound media node's current playback position (seconds). */
    public static Event mediaTimeUpdated(int target, float seconds) {
        return new Event(Commands.Event.MEDIA_TIME_UPDATED, target, 0, 0, 0, seconds, null, 0, 0);
    }

    /** A bound media node reached the end of its media. */
    public static Event mediaEnded(int target) {
        return new Event(Commands.Event.MEDIA_ENDED, target, 0, 0, 0, 0, null, 0, 0);
    }

    /** A bound media node's volume changed (0..1). */
    public static Event mediaVolumeChanged(int target, float volume) {
        return new Event(Commands.Event.MEDIA_VOLUME_CHANGED, target, 0, 0, 0, volume, null, 0, 0);
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

    /** For {@code MEDIA_PLAY_STATE_CHANGED}: the playing bit (0/1). */
    public boolean mediaPlaying() {
        return b != 0;
    }

    /** For {@code MEDIA_TIME_UPDATED}: seconds; for {@code MEDIA_VOLUME_CHANGED}: volume (0..1). */
    public float mediaValue() {
        return value;
    }

    /** For `NAVIGATE` with the `NAVIGATE_URL` flag: the destination URL; null for a back request. */
    public String url() {
        return text;
    }

    /** For `NAVIGATE` without the `NAVIGATE_URL` flag: the platform wants to go back one step. */
    public boolean isNavigateBack() {
        return isNavigate() && (flags & Commands.Flags.NAVIGATE_URL) == 0;
    }

    /** For {@code KEY_*}: the canonical logical key code. */
    public int keyCode() {
        return b;
    }

    /** For {@code KEY_*}: the modifier bitmask (Shift=1, Control=2, Alt=4, Meta=8). */
    public int modifiers() {
        return c;
    }

    /** For {@code DATE_CHANGED}: days since epoch (I32). */
    public int days() {
        return b;
    }

    /** For {@code DATE_CHANGED}: millis of day (U32, 0..86,400,000). */
    public int millisOfDay() {
        return c;
    }
}