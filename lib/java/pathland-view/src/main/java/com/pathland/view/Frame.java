package com.pathland.view;

import java.util.ArrayList;
import java.util.List;

/**
 * The SwiftUI-like {@code frame} sizing modifier. Unlike the old API, you apply
 * it with the {@link View} convenience surface — {@code .frame(width, height)},
 * {@code .frameWidth(w)} / {@code .frameHeight(h)}, and the lambda configurator
 * {@code .frame(c -> c.minWidth(..).alignment(..))} — never by constructing this
 * class directly.
 *
 * <p>The modifier compiles to the same discipline as the legacy {@code FrameMod}
 * (the wire is unchanged): a fixed {@code WIDTH}/{@code HEIGHT} ({@code ±∞}
 * normalized to {@code Commands.Size.FILL}, {@code NaN} omitting the axis), an
 * {@code ALIGNMENT} enum code, and the min/ideal/max bounds — each as an ordinary
 * {@code PARAMETER::SET_PROPERTY} ({@code 0x100B}/{@code 0x100C}/{@code 0x0002}/
 * {@code 0x0012}-{@code 0x0017}). Every value is packed as an {@code F32}, matching
 * the legacy output bit for bit.
 */
public final class Frame implements ViewModifier {

    private final Float width;
    private final Float height;
    private final Float alignment;
    private final float[] bounds;

    // Package-private: construction flows through the View surface and the
    // Builder, never directly.
    Frame(Float width, Float height, Float alignment, float[] bounds) {
        this.width = width;
        this.height = height;
        this.alignment = alignment;
        this.bounds = bounds;
    }

    @Override
    public View body(View content) {
        List<Modified.Prop> props = new ArrayList<>();
        if (width != null && !Float.isNaN(width)) {
            props.add(Modified.prop(Properties.WIDTH, fillOr(width)));
        }
        if (height != null && !Float.isNaN(height)) {
            props.add(Modified.prop(Properties.HEIGHT, fillOr(height)));
        }
        if (alignment != null) {
            props.add(Modified.prop(Properties.ALIGNMENT, alignment));
        }
        if (bounds != null) {
            addBound(props, Properties.MIN_WIDTH, bounds[0]);
            addBound(props, Properties.IDEAL_WIDTH, bounds[1]);
            addBound(props, Properties.MAX_WIDTH, bounds[2]);
            addBound(props, Properties.MIN_HEIGHT, bounds[3]);
            addBound(props, Properties.IDEAL_HEIGHT, bounds[4]);
            addBound(props, Properties.MAX_HEIGHT, bounds[5]);
        }
        return Modified.props(content, props.toArray(new Modified.Prop[0]));
    }

    private static void addBound(List<Modified.Prop> props, int property, float value) {
        // NaN = unset; an infinite max/min bound means "no limit" — both are omitted.
        if (!Float.isNaN(value) && !Float.isInfinite(value)) {
            props.add(Modified.prop(property, value));
        }
    }

    /**
     * Normalize an infinite size hint ({@code frame(maxWidth: .infinity)}) to the
     * {@code Commands.Size.FILL} sentinel; finite values pass through unchanged.
     */
    private static float fillOr(float value) {
        return Float.isInfinite(value) ? Commands.Size.FILL : value;
    }

    /**
     * The {@code .frame(c -> …)} configurator: fluent, accumulates any mix of a
     * fixed {@code width}/{@code height}, the six min/ideal/max bounds, and an
     * {@code alignment}. Fixed axes and bounds are independent wire properties,
     * so they combine freely (a fixed box with hard min/max limits, say).
     *
     * <p>Value rules match the modifier contract: {@code ±∞} on
     * {@code width}/{@code height} means {@code FILL}; {@code NaN} or {@code ∞}
     * on a bound means "no limit" (omitted).
     */
    public static final class Builder {

        private Float width;
        private Float height;
        private Float alignment;
        private final float[] bounds = new float[6];
        private final boolean[] boundSet = new boolean[6];

        /** A fixed {@code width} (finite; {@code ±∞} = {@code FILL}). */
        public Builder width(float value) {
            this.width = value;
            return this;
        }

        /** A fixed {@code height} (finite; {@code ±∞} = {@code FILL}). */
        public Builder height(float value) {
            this.height = value;
            return this;
        }

        public Builder minWidth(float value) {
            return bound(0, value);
        }

        public Builder idealWidth(float value) {
            return bound(1, value);
        }

        public Builder maxWidth(float value) {
            return bound(2, value);
        }

        public Builder minHeight(float value) {
            return bound(3, value);
        }

        public Builder idealHeight(float value) {
            return bound(4, value);
        }

        public Builder maxHeight(float value) {
            return bound(5, value);
        }

        /** Position the content inside the resulting box. */
        public Builder alignment(Alignment value) {
            this.alignment = (float) value.wire();
            return this;
        }

        private Builder bound(int index, float value) {
            this.bounds[index] = value;
            this.boundSet[index] = true;
            return this;
        }

        Frame build() {
            boolean any = false;
            for (boolean set : boundSet) {
                any |= set;
            }
            if (!any) {
                return new Frame(width, height, alignment, null);
            }
            float[] selected = new float[6];
            for (int i = 0; i < selected.length; i++) {
                selected[i] = boundSet[i] ? bounds[i] : Float.NaN;
            }
            return new Frame(width, height, alignment, selected);
        }
    }
}