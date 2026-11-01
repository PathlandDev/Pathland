package dev.pathland.view;

import dev.pathland.view.core.Modified;
import dev.pathland.view.core.protocol.Properties;
import dev.pathland.view.core.View;
import dev.pathland.view.core.ViewModifier;

/**
 * inverts the view's colors ({@code COLOR_INVERT}).
 */
public final class ColorInvert implements ViewModifier {

    /** Invert. */
    static ColorInvert with() {
        return new ColorInvert();
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.COLOR_INVERT, 1));
    }
}
