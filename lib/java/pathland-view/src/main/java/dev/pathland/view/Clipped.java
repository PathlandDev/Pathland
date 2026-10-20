package dev.pathland.view;

/**
 * clips the view to its bounds ({@code CLIPS_TO_BOUNDS}).
 */
public final class Clipped implements ViewModifier {

    /** Clip. */
    static Clipped with() {
        return new Clipped();
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.CLIPS_TO_BOUNDS, 1));
    }
}
