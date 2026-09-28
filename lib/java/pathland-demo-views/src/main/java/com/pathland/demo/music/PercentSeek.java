package com.pathland.demo.music;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.WritableSignal;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A {@link WritableSignal} that presents the player's position (stored in
 * <b>seconds</b>, as the wire carries it) as a <b>0..100 progress percent</b>
 * for the seek bar. The conversion is done entirely in the UI model: the
 * protocol's {@code MEDIA_POSITION}/{@code MEDIA_TIME_UPDATED} stay seconds, and
 * this signal only shapes how the slider reads/writes them, mapped through the
 * current track's duration.
 */
final class PercentSeek implements WritableSignal<Float> {

    private final WritableSignal<Float> seconds;
    private final Supplier<Float> duration;

    /** Wrap {@code seconds} as a percent against the (live) track {@code duration}. */
    PercentSeek(WritableSignal<Float> seconds, Supplier<Float> duration) {
        this.seconds = seconds;
        this.duration = duration;
    }

    @Override
    public Float get() {
        float d = Math.max(duration.get(), 1f);
        return clampPercent(seconds.get() / d * 100f);
    }

    @Override
    public void set(Float percent) {
        float d = Math.max(duration.get(), 1f);
        seconds.set(clampPercent(percent) / 100f * d);
    }

    @Override
    public void update(UnaryOperator<Float> fn) {
        set(fn.apply(get()));
    }

    @Override
    public Signal<Float> asReadonly() {
        return this::get;
    }

    private static float clampPercent(float p) {
        return Math.max(0f, Math.min(100f, p));
    }
}