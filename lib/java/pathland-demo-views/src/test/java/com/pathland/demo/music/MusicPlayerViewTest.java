package com.pathland.demo.music;

import com.pathland.view.Categories;
import com.pathland.view.Commands;
import com.pathland.view.Components;
import com.pathland.view.Environment;
import com.pathland.view.Properties;
import com.pathland.view.emit.Emitter;
import com.pathland.view.emit.Frame;
import com.pathland.view.emit.FrameOpcodeSink;
import com.pathland.view.emit.InputDispatcher;
import com.pathland.view.emit.MediaInput;
import com.pathland.view.emit.Opcode;
import com.pathland.view.emit.RenderResult;
import com.pathland.view.signal.Signals;
import com.pathland.view.state.InMemoryStateStore;
import com.pathland.view.state.PersistentState;
import com.pathland.view.state.StateStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The music player demo: a track library, now-playing sidebar with album art, and a
 * bottom player bar. Asserts the rendered tree (HTML/SSR path — the {@code FrameOpcodeSink}
 * frame the server renders) and that interactions drive the persisted player state.
 */
class MusicPlayerViewTest {

    private static final String COVER = "/_pathland/assets/albumart/cover1.jpg";

    private static final class Mounted {
        final RenderResult result;
        final FrameOpcodeSink sink;
        final StateStore store;

        Mounted(RenderResult result, FrameOpcodeSink sink, StateStore store) {
            this.result = result;
            this.sink = sink;
            this.store = store;
        }
    }

    private static Mounted mount() {
        StateStore store = new InMemoryStateStore();
        PersistentState state = new PersistentState(store, "session-1");
        FrameOpcodeSink sink = new FrameOpcodeSink();
        RenderResult result = new Emitter(sink).mount(new MusicPlayerView(), new Environment(state));
        return new Mounted(result, sink, store);
    }

    @Test
    void libraryRendersTracksAndAlbumArt() {
        Mounted m = mount();
        Frame frame = m.sink.frame();

        assertTrue(anySetText(frame, "Building on Solid Ground"), "first track title renders");
        assertTrue(anySetText(frame, "Rendered Free"), "a later track title renders");
        assertTrue(anySetText(frame, "The Pathland Dream"), "last track title renders");
        assertTrue(anySetText(frame, "The Foundation"), "an artist renders");
        assertFalse(anySetText(frame, "Now Playing"),
                "the now-playing sidebar starts hidden (the fit slot defaults to the compact row)");
        assertTrue(anySetText(frame, "Library"), "the library header renders");
        assertTrue(countCreate(frame, Components.IMAGE) >= 3, "album art images render");
        assertTrue(anySetPropertyString(frame, Properties.IMAGE_SOURCE, COVER),
                "the track rows reference the album cover asset");
        assertTrue(countCreate(frame, Components.SIZE_THAT_FITS) == 1,
                "the root row is a SizeThatFits fit slot");
    }

    @Test
    void fitShowsTheSidebarWhenTheParentIsWideEnough() {
        Mounted m = mount();
        // The parent row is ≥ 600pt wide: the renderer reports FIT_CHANGED(index 1)
        // and the fit sink swaps the compact row for the row with the sidebar —
        // exactly how the server routes the DOM client's fit report (spec PRIMITIVES.md).
        int slotId = m.result.fitInputs().keySet().iterator().next();
        assertFalse(anySetText(m.sink.frame(), "Now Playing"), "compact before the fit change");

        m.result.fitInputs().get(slotId).accept(1);
        Frame wide = m.sink.frame();

        assertTrue(anySetText(wide, "Now Playing"), "the now-playing sidebar renders after the fit change");
        assertTrue(anySetText(wide, "Up Next"), "the up-next pane renders");
        // The main play area is REUSED (both candidates are HSTACKs at the same
        // slot-child position), so the swap must NOT rebuild the player/library —
        // only the sidebar subtree is created/inserted. A regression: if the two
        // candidates' root components differ, the whole main area would be
        // re-created (a large delta + node churn).
        assertEquals(0, countCreate(wide, Components.AUDIO),
                "the app-driven player node is re-used, not re-created");
        assertEquals(0, countCreate(wide, Components.SIZE_THAT_FITS), "the slot itself is untouched");
        assertTrue(countCreateAnything(wide) < 40, "the fit swap is a small delta, not a tree rebuild");
    }

    @Test
    void playerBarRendersTransportAndSliders() {
        Mounted m = mount();
        Frame frame = m.sink.frame();

        assertTrue(countCreate(frame, Components.BUTTON) >= 3, "transport + track-row buttons render");
        assertTrue(countCreate(frame, Components.SLIDER) == 2, "seek + volume sliders render");
        // Seek slider: progress as a percent 0..100; volume slider: 0..1 (value 0.7).
        assertTrue(anySlider(frame, 0f, 100f), "seek slider carries its 0..100 percent range");
        assertTrue(anySlider(frame, 0f, 1f), "volume slider carries its 0..1 range");
        assertTrue(anySliderValue(frame, 0.7f), "volume slider carries the persisted value");
        // The centered bar groups: transport | now-playing | volume (| dividers).
        assertTrue(anySetText(frame, "|"), "the bar groups are separated by | dividers");
        // The app-driven audio node: source + media control properties bound.
        assertTrue(anySetPropertyString(frame, Properties.AUDIO_SOURCE, "/_pathland/assets/audio/track1.mp3"),
                "the audio node carries the current track's source");
        assertTrue(anySetProperty(frame, Properties.PLAYBACK_STATE, 0), "the audio node binds play state");
        assertTrue(anySetProperty(frame, Properties.MEDIA_POSITION, 0f), "the audio node binds position");
    }

    @Test
    void interactionDrivesPersistedPlayerState() {
        Mounted m = mount();
        // Run every tap (hosts drive taps exactly like this); whichever button fires,
        // it must write the app's player state through the store.
        for (Runnable tap : m.result.tapActions().values()) {
            tap.run();
        }
        Optional<Integer> track = m.store.load("player.track:session-1", Integer.class);
        Optional<Boolean> playing = m.store.load("player.playing:session-1", Boolean.class);
        assertTrue(track.isPresent() || playing.isPresent(),
                "a player interaction persisted state (track and/or playing)");
    }

    @Test
    void mediaEventsDriveThePlayerState() {
        StateStore store = new InMemoryStateStore();
        PersistentState state = new PersistentState(store, "session-1");
        FrameOpcodeSink sink = new FrameOpcodeSink();
        RenderResult result = new Emitter(sink).mount(new MusicPlayerView(), new Environment(state));
        InputDispatcher dispatcher = new InputDispatcher(result, Signals.signal("/"));

        // The app-driven audio node's media events (spec/EVENTS.md Media) route
        // through the dispatcher into the bound player state.
        var entry = result.mediaInputs().entrySet().iterator().next();
        int audioId = entry.getKey();
        MediaInput media = entry.getValue();
        dispatcher.dispatch(com.pathland.view.transport.Event.mediaTimeUpdated(audioId, 12.5f));
        assertEquals(Optional.of(12.5f), store.load("player.position:session-1", Float.class),
                "time updates the position state");
        media.onVolumeChanged(0.5f);
        assertEquals(Optional.of(0.5f), store.load("player.volume:session-1", Float.class),
                "volume updates the volume state");
        media.onEnded();
        assertTrue(store.load("player.track:session-1", Integer.class).isPresent(),
                "ended advances the track");
    }

    @Test
    void timeUpdatesDoNotEchoBackAsMediaPosition() {
        StateStore store = new InMemoryStateStore();
        PersistentState state = new PersistentState(store, "session-3");
        FrameOpcodeSink sink = new FrameOpcodeSink();
        RenderResult result = new Emitter(sink).mount(new MusicPlayerView(), new Environment(state));
        InputDispatcher dispatcher = new InputDispatcher(result, Signals.signal("/"));
        int audioId = result.mediaInputs().entrySet().iterator().next().getKey();

        // A MEDIA_TIME_UPDATED report updates the seek-bar DISPLAY but MUST NOT be
        // echoed back as a MEDIA_POSITION seek command (spec/EVENTS.md Media) —
        // that per-second echo is what caused the seek jitter. The seek command
        // is written only by a user drag (seekRequest), never by a report.
        dispatcher.dispatch(com.pathland.view.transport.Event.mediaTimeUpdated(audioId, 30f));
        Frame delta = sink.frame();
        assertFalse(anyProperty(delta, Properties.MEDIA_POSITION),
                "a position report must not emit a MEDIA_POSITION seek");
    }

    private static int countCreate(Frame frame, int component) {
        int n = 0;
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.TREE && op.command() == Commands.Tree.CREATE_NODE
                    && op.b() == component) {
                n++;
            }
        }
        return n;
    }

    private static int countCreateAnything(Frame frame) {
        int n = 0;
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.TREE && op.command() == Commands.Tree.CREATE_NODE) {
                n++;
            }
        }
        return n;
    }

    private static boolean anySlider(Frame frame, float min, float max) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() != Categories.PARAMETER || op.command() != Commands.Parameter.SET_PROPERTY) {
                continue;
            }
            if ((op.b() & 0xFFFF) == Properties.MIN_VALUE
                    && Float.intBitsToFloat(op.c()) == min) {
                return hasProperty(frame, Properties.MAX_VALUE, max);
            }
        }
        return false;
    }

    private static boolean anySliderValue(Frame frame, float value) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.PARAMETER && op.command() == Commands.Parameter.SET_PROPERTY
                    && (op.b() & 0xFFFF) == Properties.VALUE
                    && Float.intBitsToFloat(op.c()) == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasProperty(Frame frame, int property, float value) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.PARAMETER && op.command() == Commands.Parameter.SET_PROPERTY
                    && (op.b() & 0xFFFF) == property && Float.intBitsToFloat(op.c()) == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean anySetText(Frame frame, String text) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.PARAMETER && op.command() == Commands.Parameter.SET_TEXT
                    && text.equals(frame.stringAt(op.b()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean anySetProperty(Frame frame, int property, int value) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.PARAMETER && op.command() == Commands.Parameter.SET_PROPERTY
                    && (op.b() & 0xFFFF) == property && op.c() == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyProperty(Frame frame, int property) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.PARAMETER && op.command() == Commands.Parameter.SET_PROPERTY
                    && (op.b() & 0xFFFF) == property) {
                return true;
            }
        }
        return false;
    }

    private static boolean anySetProperty(Frame frame, int property, float value) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.PARAMETER && op.command() == Commands.Parameter.SET_PROPERTY
                    && (op.b() & 0xFFFF) == property && Float.intBitsToFloat(op.c()) == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean anySetPropertyString(Frame frame, int property, String value) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.PARAMETER && op.command() == Commands.Parameter.SET_PROPERTY
                    && (op.b() & 0xFFFF) == property
                    && value.equals(frame.stringAt(op.c()))) {
                return true;
            }
        }
        return false;
    }
}