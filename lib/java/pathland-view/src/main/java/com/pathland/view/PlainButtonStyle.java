package com.pathland.view;

/**
 * The default button style: the label alone. The {@link Button} control owns the
 * native {@code BUTTON} node and its action, so this style contributes only content.
 */
public enum PlainButtonStyle implements ButtonStyle {

    INSTANCE;

    @Override
    public View makeBody(Configuration config) {
        return config.label();
    }
}
