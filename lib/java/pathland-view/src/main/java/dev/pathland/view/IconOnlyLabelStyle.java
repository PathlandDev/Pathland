package dev.pathland.view;

import dev.pathland.view.core.LabelStyle;
import dev.pathland.view.core.View;

/**
 * A label style that shows only the icon ({@code IconOnlyLabelStyle}).
 * The title drives the accessibility label even though it is not shown.
 */
public enum IconOnlyLabelStyle implements LabelStyle {

    INSTANCE;

    @Override
    public View makeBody(Configuration config) {
        return config.hasIcon() ? config.icon() : Group.children();
    }
}
