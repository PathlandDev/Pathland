package dev.pathland.demo.kitchensink;

import dev.pathland.view.Frame;
import dev.pathland.view.Alignment;
import dev.pathland.view.Padding;
import dev.pathland.view.Text;
import dev.pathland.view.Video;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import static dev.pathland.view.Modifiers.*;

/**
 * Media section: a {@link Video} surface. Video playback interaction
 * (play/pause/volume/seek) is **renderer-native** by default. A video bound to a
 * {@link dev.pathland.view.MediaController} attaches the controller with
 * {@code controller.attach()} and references it with {@code Video.make(controller)}
 * (spec/MEDIA.md); this bare surface exercises the `VIDEO` primitive alone.
 */
public final class MediaSection implements View {

    @Override
    public View body() {
        View content = VStack.make()
                .modifiers(padding(4))
                .children(
                        Video.make()
                                .modifiers(frame(f -> f.width(320).height(180).alignment(Alignment.CENTER))),
                        Text.make("Playback controls are native — the app only supplies the source."));
        return SectionCard.make("Media · video (renderer-native controls)").children(content);
    }
}
