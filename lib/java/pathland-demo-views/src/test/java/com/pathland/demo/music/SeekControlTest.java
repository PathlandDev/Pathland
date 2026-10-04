package com.pathland.demo.music;

import com.pathland.view.signal.WritableSignal;
import com.pathland.view.signal.Signals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The seek slider's seek-on-release contract: during a drag session
 * ({@code EDITING_CHANGED} true) values are buffered, and the seek COMMAND is
 * committed once when the drag ends — one seek per drag, not one per tick.
 */
class SeekControlTest {

    @Test
    void buffersDuringEditingAndCommitsOnRelease() {
        WritableSignal<Float> position = Signals.signal(0f);
        WritableSignal<Float> seekRequest = Signals.signal(0f);
        SeekControl seek = new SeekControl(position, seekRequest);

        seek.onEditingChanged(true);
        seek.set(30f);
        seek.set(45f);
        assertEquals(0f, seekRequest.get(), "no seek command during the drag");
        assertEquals(0f, position.get(), "the display stays until the commit");

        seek.onEditingChanged(false);
        assertEquals(45f, seekRequest.get(), "the last drag position commits on release");
        assertEquals(45f, position.get(), "the display follows the commit");
    }

    @Test
    void nonDragChangeCommitsImmediately() {
        WritableSignal<Float> position = Signals.signal(0f);
        WritableSignal<Float> seekRequest = Signals.signal(0f);
        SeekControl seek = new SeekControl(position, seekRequest);

        seek.set(10f);
        assertEquals(10f, seekRequest.get(), "a non-drag change seeks immediately");
        assertEquals(10f, position.get());
    }
}