package dev.pathland.view;

import dev.pathland.view.core.Modified;
import dev.pathland.view.core.protocol.Properties;
import dev.pathland.view.core.View;
import dev.pathland.view.core.ViewModifier;

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
