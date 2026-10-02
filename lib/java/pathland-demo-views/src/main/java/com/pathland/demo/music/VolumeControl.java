package com.pathland.demo.music;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.WritableSignal;

import java.util.function.UnaryOperator;

/**
 * A {@link WritableSignal} (0..1) for the volume slider that **presents** the
 * current volume (the {@code MEDIA_VOLUME_CHANGED} display signal) but on a user
 * drag writes the **volume command** signal bound to {@code MEDIA_VOLUME} (and
 * the display, for immediate feedback). Decoupling stops a volume report from
 * echoing back as a command (spec/EVENTS.md Media).
 */
final class VolumeControl implements WritableSignal<Float> {

    private final WritableSignal<Float> volume;
    private final WritableSignal<Float> volumeRequest;

    VolumeControl(WritableSignal<Float> volume, WritableSignal<Float> volumeRequest) {
        this.volume = volume;
        this.volumeRequest = volumeRequest;
    }

    @Override
    public Float get() {
        return volume.get();
    }

    @Override
    public void set(Float value) {
        volume.set(value);
        volumeRequest.set(value);
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