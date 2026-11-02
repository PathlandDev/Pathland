package dev.pathland.view;

import dev.pathland.view.core.EmptyContent;
import dev.pathland.view.core.VideoStyle;
import dev.pathland.view.core.View;

/** The default video style: no content — the {@link Video} control renders its
 *  native {@code VIDEO} shell and the renderer shows native media controls. */
public enum NativeVideoStyle implements VideoStyle {

    INSTANCE;

    @Override
    public View makeBody(Configuration config) {
        return EmptyContent.INSTANCE;
    }
}