package dev.pathland.view;

import dev.pathland.view.core.ContentMode;
import dev.pathland.view.core.Modified;
import dev.pathland.view.core.protocol.Properties;
import dev.pathland.view.core.View;
import dev.pathland.view.core.ViewModifier;

/**
 * fits the content within its bounds ({@code CONTENT_MODE=Fit}).
 */
public final class ScaledToFit implements ViewModifier {

    /** Fit. */
    static ScaledToFit with() {
        return new ScaledToFit();
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.CONTENT_MODE, (float) ContentMode.FIT.wire()));
    }
}
