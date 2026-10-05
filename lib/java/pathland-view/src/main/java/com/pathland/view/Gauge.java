package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

/** A read-only value shown against a scale ({@code Gauge}). */
public final class Gauge implements View {

    private final float value;
    private final float min;
    private final float max;

    private Gauge(float value, float min, float max) {
        this.value = value;
        this.min = min;
        this.max = max;
    }

    /** A read-only range meter. */
    public static Gauge of(float value, float min, float max) {
        return new Gauge(value, min, max);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.GAUGE);
        node.properties.put(Properties.VALUE, value);
        node.properties.put(Properties.MIN_VALUE, min);
        node.properties.put(Properties.MAX_VALUE, max);
        return node;
    }
}