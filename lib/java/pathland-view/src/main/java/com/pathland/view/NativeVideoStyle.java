package com.pathland.view;

/** The default video style: no content — the {@link Video} control renders its
 *  native {@code VIDEO} shell and the renderer shows native media controls. */
public enum NativeVideoStyle implements VideoStyle {

    INSTANCE;

    @Override
    public View makeBody(Configuration config) {
        return EmptyContent.INSTANCE;
    }
}