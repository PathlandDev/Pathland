package com.pathland.view;

/**
 * the compound {@code frame} sizing modifier ({@code WIDTH} / {@code HEIGHT} / {@code ALIGNMENT} / min-max bounds).
 */
public final class FrameMod implements ViewModifier {

    private final Float width;
    private final Float height;
    private final Float alignment;
    private final float[] bounds;

    private FrameMod(Float width, Float height, Float alignment, float[] bounds) {
        this.width = width;
        this.height = height;
        this.alignment = alignment;
        this.bounds = bounds;
    }

    /**
     * A frame of a fixed {@code width} with **no height hint** and no content
     * alignment: the view keeps its natural height and, as a flex child, stretches
     * to its container's cross axis (e.g. a sidebar column filling a row's full
     * height). Alignment is left untouched, so a stack's own cross-axis alignment
     * (constructor {@code Alignment}) is not overwritten.
     */
    public static FrameMod of(float width) {
        return new FrameMod(width, null, null, null);
    }

    /** A frame of {@code width} x {@code height} with no content alignment. */
    public static FrameMod of(float width, float height) {
        return new FrameMod(width, height, null, null);
    }

    /** A frame of {@code width} x {@code height} at {@code alignment}. */
    public static FrameMod of(float width, float height, Alignment alignment) {
        return new FrameMod(width, height, (float) alignment.wire(), null);
    }

    /**
     * A frame of a fixed {@code width} at {@code alignment} with **no height hint**:
     * the view keeps its natural height and, as a flex child, stretches to its
     * container's cross axis (e.g. a sidebar column filling a row's full height).
     */
    public static FrameMod of(float width, Alignment alignment) {
        return new FrameMod(width, null, (float) alignment.wire(), null);
    }

    /** A min/ideal/max frame; pass {@link Float#NaN} for any unset bound. */
    public static FrameMod of(float minWidth, float idealWidth, float maxWidth,
                              float minHeight, float idealHeight, float maxHeight) {
        return new FrameMod(null, null, null,
                new float[] { minWidth, idealWidth, maxWidth, minHeight, idealHeight, maxHeight });
    }

    @Override
    public View body(View content) {
        if (bounds != null) {
            java.util.List<Modified.Prop> props = new java.util.ArrayList<>();
            addBound(props, Properties.MIN_WIDTH, bounds[0]);
            addBound(props, Properties.IDEAL_WIDTH, bounds[1]);
            addBound(props, Properties.MAX_WIDTH, bounds[2]);
            addBound(props, Properties.MIN_HEIGHT, bounds[3]);
            addBound(props, Properties.IDEAL_HEIGHT, bounds[4]);
            addBound(props, Properties.MAX_HEIGHT, bounds[5]);
            return Modified.props(content, props.toArray(new Modified.Prop[0]));
        }
        java.util.List<Modified.Prop> props = new java.util.ArrayList<>();
        if (width != null) {
            props.add(Modified.prop(Properties.WIDTH, width));
        }
        if (height != null) {
            props.add(Modified.prop(Properties.HEIGHT, height));
        }
        if (alignment != null) {
            props.add(Modified.prop(Properties.ALIGNMENT, alignment));
        }
        return Modified.props(content, props.toArray(new Modified.Prop[0]));
    }

    private static void addBound(java.util.List<Modified.Prop> props, int property, float value) {
        if (!Float.isNaN(value)) {
            props.add(Modified.prop(property, value));
        }
    }
}
