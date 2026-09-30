package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

/** The default audio style: no control children — the renderer shows native
 *  media controls ({@code <audio controls>}); the app supplies only the source. */
public enum NativeAudioStyle implements AudioStyle {

    INSTANCE;

    @Override
    public View makeBody(Configuration config) {
        return MediaBody.INSTANCE;
    }

    /** Renders a bare media shell; {@link Audio#render} forces the AUDIO
     *  component and adds the media source + control properties. */
    private enum MediaBody implements View {
        INSTANCE;

        @Override
        public PathlandNode render(Environment env) {
            return new PathlandNode(Components.AUDIO);
        }
    }
}