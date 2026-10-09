package com.pathland.view;

import java.util.ArrayList;
import java.util.List;

/**
 * The default label style ({@code DefaultLabelStyle}): the icon and title
 * shown together in a horizontal row. Absent parts are omitted.
 */
public enum DefaultLabelStyle implements LabelStyle {

    INSTANCE;

    @Override
    public View makeBody(Configuration config) {
        List<View> parts = new ArrayList<>(2);
        if (config.hasIcon()) {
            parts.add(config.icon());
        }
        if (config.hasTitle()) {
            parts.add(config.title());
        }
        return HStack.with(h -> h.alignment(VerticalAlignment.CENTER).spacing(2f))
                .children(parts.toArray(new View[0]));
    }
}
