package dev.pathland.view;

import dev.pathland.view.core.Modified;
import dev.pathland.view.core.protocol.Properties;
import dev.pathland.view.core.View;
import dev.pathland.view.core.ViewModifier;

/**
 * hides the view ({@code VISIBLE}=0).
 */
public final class Hidden implements ViewModifier {

    /** Hidden. */
    static Hidden with() {
        return new Hidden();
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.VISIBLE, 0));
    }
}
