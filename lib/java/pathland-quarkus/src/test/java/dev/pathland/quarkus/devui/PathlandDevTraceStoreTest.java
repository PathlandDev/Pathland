package dev.pathland.quarkus.devui;

import dev.pathland.view.core.protocol.Categories;
import dev.pathland.view.core.protocol.Commands;
import dev.pathland.view.core.protocol.Opcode;
import dev.pathland.view.core.transport.Event;
import dev.pathland.view.core.transport.FrameCodec;
import dev.pathland.view.core.transport.ProtocolFrame;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bounded dev trace ring: bounded retention, cursors, decode, and clear. */
class PathlandDevTraceStoreTest {

    @Test
    void decodesBatchesIncludingTheTextField() throws Exception {
        PathlandDevTraceStore store = new PathlandDevTraceStore();
        byte[] text = "hello".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream strings = new ByteArrayOutputStream();
        strings.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(text.length).array());
        strings.write(text);
        Opcode setText = new Opcode(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 1, 0, 0);
        byte[] batch = FrameCodec.encodeFrame(
                new ProtocolFrame(List.of(setText), strings.toByteArray()), 4);

        store.batchSent("s-1", "/", 4, 1, batch);

        PathlandDevTraceStore.Entry entry = store.snapshot(0, 10).entries().get(0);
        assertEquals(PathlandDevTraceStore.Kind.BATCH, entry.kind());
        assertEquals(4, entry.sequence());
        assertEquals(1, entry.frames());
        assertEquals(1, entry.opcodes());
        assertEquals(batch.length, entry.bytes());
        assertEquals(Categories.PARAMETER, entry.decoded().get(0).category());
        assertEquals(Commands.Parameter.SET_TEXT, entry.decoded().get(0).command());
        assertEquals("hello", entry.decoded().get(0).text());
        assertNull(entry.error());
    }

    @Test
    void retainsAllKindsAndHonorsTheCursor() {
        PathlandDevTraceStore store = new PathlandDevTraceStore();
        store.sessionOpened("s-1", "/");
        store.eventsReceived("s-1", "/", List.of(Event.pointerUp(1, 0, 0)));
        store.ssrRendered("/", "/home", 1_500_000L, 4096);
        store.sessionClosed("s-1", "/");

        PathlandDevTraceStore.Snapshot all = store.snapshot(0, 10);
        assertEquals(4, all.entries().size());
        assertEquals(4, all.nextId());
        assertEquals(List.of(
                        PathlandDevTraceStore.Kind.SESSION_OPEN,
                        PathlandDevTraceStore.Kind.EVENTS,
                        PathlandDevTraceStore.Kind.SSR,
                        PathlandDevTraceStore.Kind.SESSION_CLOSE),
                all.entries().stream().map(PathlandDevTraceStore.Entry::kind).toList());
        assertEquals(1, all.entries().get(1).events().size());
        assertEquals("/home", all.entries().get(2).route());
        assertEquals(4096, all.entries().get(2).bytes());

        PathlandDevTraceStore.Snapshot since = store.snapshot(2, 10);
        assertEquals(2, since.entries().size());
        assertEquals(3, since.entries().get(0).id());
        assertEquals(4, since.entries().get(1).id());
    }

    @Test
    void dropsTheOldestEntriesAndKeepsIncreasingIds() {
        PathlandDevTraceStore store = new PathlandDevTraceStore();
        int overflow = 5;
        for (int i = 0; i < PathlandDevTraceStore.MAX_ENTRIES + overflow; i++) {
            store.sessionOpened("s-" + i, "/");
        }
        assertEquals(PathlandDevTraceStore.MAX_ENTRIES, store.size());
        PathlandDevTraceStore.Snapshot snapshot = store.snapshot(0, PathlandDevTraceStore.MAX_ENTRIES);
        assertEquals(PathlandDevTraceStore.MAX_ENTRIES, snapshot.entries().size());
        assertEquals(overflow + 1L, snapshot.entries().get(0).id(),
                "the oldest entries were dropped");
        assertEquals(PathlandDevTraceStore.MAX_ENTRIES + overflow, snapshot.nextId());

        store.clear();
        assertEquals(0, store.size());
        assertEquals(PathlandDevTraceStore.MAX_ENTRIES + overflow, store.lastId(),
                "clear keeps the high-water id");
        assertTrue(store.snapshot(0, 10).entries().isEmpty());
    }
}
