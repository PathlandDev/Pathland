package com.pathland.view;

/** The default button style: the label alone, wired to the action on tap. */
public enum PlainButtonStyle implements ButtonStyle {

    INSTANCE;

    @Override
    public View makeBody(Configuration config) {
        return config.label().with(TapGesture.of(config.action()));
    }
}