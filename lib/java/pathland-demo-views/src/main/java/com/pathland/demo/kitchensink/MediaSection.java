package com.pathland.demo.kitchensink;

import com.pathland.view.Frame;
import com.pathland.view.Alignment;
import com.pathland.view.Audio;
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
                VStack.children(
                        Video.with(v -> v.source("https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"))
                                .modifiers(Frame.with(f -> f.width(320).height(180).alignment(Alignment.CENTER))),
                        Audio.with(a -> a.source("https://interactive-examples.mdn.mozilla.net/media/cc0-audio/t-rex-roar.mp3")),
                        Text.with(t -> t.text("Playback controls are native — the app only supplies the source."))
                ).modifiers(Padding.with(p -> p.uniform(4)))
        );
    }
}