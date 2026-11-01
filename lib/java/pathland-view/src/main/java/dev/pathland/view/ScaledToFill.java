package dev.pathland.view;

import dev.pathland.view.core.ContentMode;
import dev.pathland.view.core.Modified;
import dev.pathland.view.core.protocol.Properties;
import dev.pathland.view.core.View;
import dev.pathland.view.core.ViewModifier;

/**
 * fills its bounds, cropping overflow ({@code CONTENT_MODE=Fill}).
 */
public final class ScaledToFill implements ViewModifier {

    /** Fill. */
    static ScaledToFill with() {
        return new ScaledToFill();
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.CONTENT_MODE, (float) ContentMode.FILL.wire()));
    }
}
