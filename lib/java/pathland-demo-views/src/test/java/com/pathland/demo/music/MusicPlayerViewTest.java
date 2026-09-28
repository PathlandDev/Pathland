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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The music player demo: a track library, now-playing sidebar with album art, and a
 * bottom player bar. Asserts the rendered tree (HTML/SSR path — the {@code FrameOpcodeSink}
 * frame the server renders) and that interactions drive the persisted player state.
 */
class MusicPlayerViewTest {

    private static final String COVER = "/_pathland/assets/albumart/cover1.svg";

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

        assertTrue(anySetText(frame, "Midnight Drive"), "first track title renders");
        assertTrue(anySetText(frame, "Blue Hour"), "a later track title renders");
        assertTrue(anySetText(frame, "Tidal"), "last track title renders");
        assertTrue(anySetText(frame, "The Neon Signals"), "an artist renders");
        assertTrue(anySetText(frame, "Now Playing"), "the now-playing sidebar header renders");
        assertTrue(anySetText(frame, "Library"), "the library header renders");
        assertTrue(countCreate(frame, Components.IMAGE) >= 3, "album art images render");
        assertTrue(anySetPropertyString(frame, Properties.IMAGE_SOURCE, COVER),
                "the track rows reference the album cover asset");
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
        assertTrue(countCreate(frame, Components.AUDIO) == 1, "one audio node drives playback");
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

    private static boolean anySlider(Frame frame, float min, float max) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() != Categories.STYLE || op.command() != Commands.Style.SET_PROPERTY) {
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
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_PROPERTY
                    && (op.b() & 0xFFFF) == Properties.VALUE
                    && Float.intBitsToFloat(op.c()) == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasProperty(Frame frame, int property, float value) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_PROPERTY
                    && (op.b() & 0xFFFF) == property && Float.intBitsToFloat(op.c()) == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean anySetText(Frame frame, String text) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_TEXT
                    && text.equals(frame.stringAt(op.b()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean anySetProperty(Frame frame, int property, int value) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_PROPERTY
                    && (op.b() & 0xFFFF) == property && op.c() == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean anySetProperty(Frame frame, int property, float value) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_PROPERTY
                    && (op.b() & 0xFFFF) == property && Float.intBitsToFloat(op.c()) == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean anySetPropertyString(Frame frame, int property, String value) {
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_PROPERTY
                    && (op.b() & 0xFFFF) == property
                    && value.equals(frame.stringAt(op.c()))) {
                return true;
            }
        }
        return false;
    }
}