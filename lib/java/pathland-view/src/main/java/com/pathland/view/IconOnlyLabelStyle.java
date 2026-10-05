package com.pathland.view;

/**
 * A label style that shows only the icon ({@code IconOnlyLabelStyle}).
 * The title drives the accessibility label even though it is not shown.
 */
public enum IconOnlyLabelStyle implements LabelStyle {

    INSTANCE;

    @Override
    public View makeBody(Configuration config) {
        return config.hasIcon() ? config.icon() : Group.of();
    }
}
