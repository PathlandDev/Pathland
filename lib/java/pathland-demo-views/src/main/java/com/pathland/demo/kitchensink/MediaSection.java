package com.pathland.demo.kitchensink;

import com.pathland.view.Alignment;
import com.pathland.view.Audio;
import com.pathland.view.FrameMod;
import com.pathland.view.Padding;
import com.pathland.view.Text;
import com.pathland.view.Video;
import com.pathland.view.VStack;
import com.pathland.view.View;

/**
 * Media section: a {@link Video} and an {@link Audio} node. Playback interaction
 * (play/pause/volume/seek) is **renderer-native** — the app supplies only the
 * source reference (an asset ref; here remote sample URLs) and size/layout.
 */
public final class MediaSection implements View {

    @Override
    public View body() {
        return new SectionCard("Media · video + audio (renderer-native controls)",
                VStack.of(
                        Video.of("https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4")
                                .modifier(FrameMod.of(320, 180, Alignment.CENTER)),
                        Audio.of("https://interactive-examples.mdn.mozilla.net/media/cc0-audio/t-rex-roar.mp3"),
                        Text.of("Playback controls are native — the app only supplies the source.")
                ).modifier(Padding.of(4))
        );
    }
}