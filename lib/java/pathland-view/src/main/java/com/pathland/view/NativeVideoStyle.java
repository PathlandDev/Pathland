package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

/** The default video style: no control children — native media controls. */
public enum NativeVideoStyle implements VideoStyle {

    INSTANCE;

    @Override
    public View makeBody(Configuration config) {
        return MediaBody.INSTANCE;
    }

    /** Renders a bare media shell; {@link Video#render} forces the VIDEO
     *  component and adds the media source + control properties. */
    private enum MediaBody implements View {
        INSTANCE;

        @Override
        public PathlandNode render(Environment env) {
            return new PathlandNode(Components.VIDEO);
        }
    }
}