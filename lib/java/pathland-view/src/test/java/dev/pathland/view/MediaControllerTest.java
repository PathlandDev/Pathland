package dev.pathland.view;

import dev.pathland.view.emit.Emitter;
import dev.pathland.view.emit.FrameOpcodeSink;
import dev.pathland.view.emit.MediaInput;
import dev.pathland.view.emit.RenderResult;
import dev.pathland.view.signal.Signals;
import dev.pathland.view.signal.WritableSignal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** `MediaController` commands vs. reports (spec/MEDIA.md §4.2). */
class MediaControllerTest {

    /**
     * A renderer's `MEDIA_PLAY_STATE_CHANGED` is a **report**: it feeds
     * `playingReport()`, never the app's `PLAYBACK_STATE` command. Otherwise the
     * natural-end stop would write "pause", and the next track (advanced on
     * `MEDIA_ENDED`) would load paused instead of continuing.
     */
    @Test
    void playStateReportDoesNotEchoBackAsAPlaybackCommand() {
        WritableSignal<Boolean> command = Signals.signal(true);
        MediaController player = new MediaController();
        player.source("/track.mp3");
        player.playing(command);

        FrameOpcodeSink sink = new FrameOpcodeSink();
        RenderResult result = new Emitter(sink).mount(player.attach(), Environment.DEFAULT);
        int controller = result.mediaInputs().keySet().iterator().next();
        MediaInput media = result.mediaInputs().get(controller);

        long framesBefore = sink.framesProduced();
        media.onPlayStateChanged(false);

        assertEquals(framesBefore, sink.framesProduced(),
                "a play-state report emits no PLAYBACK_STATE command");
        assertFalse(player.playingReport().get(), "the report feed is updated");
        assertTrue(command.get(),
                "the app's play command is untouched (the next track keeps playing)");
    }
}
