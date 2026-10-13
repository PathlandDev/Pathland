package dev.pathland.demo.kitchensink;

import dev.pathland.view.Frame;
import dev.pathland.view.Alignment;
import dev.pathland.view.Audio;
import dev.pathland.view.Padding;
import dev.pathland.view.Text;
import dev.pathland.view.Video;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import static dev.pathland.view.Modifiers.*;

/**
 * Media section: a {@link Video} and an {@link Audio} node. Playback interaction
 * (play/pause/volume/seek) is **renderer-native** — the app supplies only the
 * source reference (an asset ref; here remote sample URLs) and size/layout.
 */
public final class MediaSection implements View {

    @Override
    public View body() {
        View content = VStack.make()
                .modifiers(padding(4))
                .children(
                        Video.make("https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4")
                                .modifiers(frame(f -> f.width(320).height(180).alignment(Alignment.CENTER))),
                        Audio.make("https://interactive-examples.mdn.mozilla.net/media/cc0-audio/t-rex-roar.mp3"),
                        Text.make("Playback controls are native — the app only supplies the source."));
        return SectionCard.make("Media · video + audio (renderer-native controls)").children(content);
    }
}