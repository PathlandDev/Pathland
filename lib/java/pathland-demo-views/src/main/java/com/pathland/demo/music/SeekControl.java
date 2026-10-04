package com.pathland.demo.music;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.WritableSignal;

import java.util.function.UnaryOperator;

/**
 * A {@link WritableSignal} (seconds) for the seek slider that **presents** the
 * playback position (the {@code MEDIA_TIME_UPDATED} display signal) but on a user
 * drag writes the **seek command** signal bound to {@code MEDIA_POSITION} (and
 * the display, for immediate thumb feedback). Decoupling the two is what stops a
 * position report from echoing back as a seek (spec/EVENTS.md Media).
 *
 * <p><b>Seek-on-release</b>: during a drag session ({@code EDITING_CHANGED} true —
 * reported by the renderers when the slider declares the {@code EDITING} listener
 * bit) values are only buffered; the seek command is committed once when the drag
 * ends ({@code onEditingChanged(false)}), so the player seeks a single time per
 * drag instead of once per drag tick.
 */
final class SeekControl implements WritableSignal<Float> {

    private final WritableSignal<Float> position;
    private final WritableSignal<Float> seekRequest;
    private boolean editing;
    private float pendingSeek;

    SeekControl(WritableSignal<Float> position, WritableSignal<Float> seekRequest) {
        this.position = position;
        this.seekRequest = seekRequest;
    }

    @Override
    public Float get() {
        return position.get();
    }

    @Override
    public void set(Float seconds) {
        pendingSeek = seconds;
        // While dragging, the thumb is authoritative and inbound VALUE is
        // suppressed by the renderer — buffer the command and commit on release.
        if (!editing) {
            commit();
        }
    }

    /** {@code EDITING_CHANGED} boundary: a drag session began (true) / ended (false). */
    void onEditingChanged(boolean editing) {
        this.editing = editing;
        if (!editing) {
            commit();
        }
    }

    private void commit() {
        position.set(pendingSeek);
        seekRequest.set(pendingSeek);
    }

    @Override
    public void update(UnaryOperator<Float> fn) {
        set(fn.apply(get()));
    }

    @Override
    public Signal<Float> asReadonly() {
        return this::get;
    }
}