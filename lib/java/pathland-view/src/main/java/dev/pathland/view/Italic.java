package dev.pathland.view;

import dev.pathland.view.core.FontStyle;
import dev.pathland.view.core.Modified;
import dev.pathland.view.core.protocol.Properties;
import dev.pathland.view.core.View;
import dev.pathland.view.core.ViewModifier;

/**
 * italicizes the text ({@code FONT_STYLE=Italic}).
 */
public final class Italic implements ViewModifier {

    /** Italic. */
    static Italic with() {
        return new Italic();
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.FONT_STYLE, (float) FontStyle.ITALIC.wire()));
    }
}
