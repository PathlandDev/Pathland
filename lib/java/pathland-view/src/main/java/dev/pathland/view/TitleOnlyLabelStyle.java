package dev.pathland.view;

import dev.pathland.view.core.LabelStyle;
import dev.pathland.view.core.View;

/**
 * A label style that shows only the title ({@code TitleOnlyLabelStyle}).
 * The icon is omitted; the title still drives the accessibility label.
 */
public enum TitleOnlyLabelStyle implements LabelStyle {

    INSTANCE;

    @Override
    public View makeBody(Configuration config) {
        return config.hasTitle() ? config.title() : Group.children();
    }
}
