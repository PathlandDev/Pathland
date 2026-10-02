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
 */
final class SeekControl implements WritableSignal<Float> {

    private final WritableSignal<Float> position;
    private final WritableSignal<Float> seekRequest;

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
        // A user seek: update the thumb immediately AND emit the seek command.
        position.set(seconds);
        seekRequest.set(seconds);
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