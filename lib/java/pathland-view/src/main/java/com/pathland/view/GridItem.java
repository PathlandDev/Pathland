package com.pathland.view;

/** A single grid track spec ({@code GridItem} / Compose {@code GridCells}
 *  parity). Serializes to a `GRID_TRACKS` token — `flex`, `fixed:<points>`, or
 *  `adaptive:<points>` (spec/PRIMITIVES.md §grid model). */
public final class GridItem {

    private final String token;

    private GridItem(String token) {
        this.token = token;
    }

    /** An equal `1fr` share (what a bare count means). */
    public static GridItem flexible() {
        return new GridItem("flex");
    }

    /** Exactly {@code points} wide. */
    public static GridItem fixed(float points) {
        return new GridItem("fixed:" + fmt(points));
    }

    /** Auto-fit, at least {@code minPoints} wide (fit as many as fit). */
    public static GridItem adaptive(float minPoints) {
        return new GridItem("adaptive:" + fmt(minPoints));
    }

    private static String fmt(float v) {
        return v == Math.floor(v) && !Float.isInfinite(v) ? String.valueOf((long) v) : Float.toString(v);
    }

    /** The serialized `GRID_TRACKS` token. */
    public String token() {
        return token;
    }
}