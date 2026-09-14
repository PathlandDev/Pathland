package com.pathland.demo.gtk;

import com.pathland.view.Commands;
import com.pathland.view.transport.Event;
import com.sun.jna.Memory;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless: decodes raw ring EVENT opcodes + event-arena strings, no GTK. */
class RingEventReaderTest {

    private static final int CATEGORY_EVENT = 0x03;
    private static final int OPCODE_SIZE = 16;
    private static final int OFF_EVENT_ARENA_OFFSET = 0x40;
    private static final int HEADER_SIZE = 0x50;

    private static byte[] opcode(int command, int flags, int a, int b, int c) {
        byte[] raw = new byte[OPCODE_SIZE];
        raw[0] = (byte) CATEGORY_EVENT;
        raw[1] = (byte) command;
        raw[2] = (byte) (flags & 0xFF);
        raw[3] = (byte) ((flags >>> 8) & 0xFF);
        putLe(raw, 4, a);
        putLe(raw, 8, b);
        putLe(raw, 12, c);
        return raw;
    }

    private static void putLe(byte[] raw, int off, int value) {
        raw[off] = (byte) (value & 0xFF);
        raw[off + 1] = (byte) ((value >>> 8) & 0xFF);
        raw[off + 2] = (byte) ((value >>> 16) & 0xFF);
        raw[off + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    /** A fake shared ring: header + an event arena holding one string at a known ref. */
    private static Memory ringWithArena(int arenaOffset, String text, int textRef) {
        Memory ring = new Memory(arenaOffset + 64);
        ring.setInt(OFF_EVENT_ARENA_OFFSET, arenaOffset);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        // [u32 len][bytes] at arenaOffset + textRef
        ring.setInt(arenaOffset + textRef, bytes.length);
        ring.write(arenaOffset + textRef + 4, bytes, 0, bytes.length);
        return ring;
    }

    @Test
    void decodesPointerValueAndDateEvents() {
        try (Memory ring = ringWithArena(HEADER_SIZE, "typed text", 0)) {
            RingEventReader reader = new RingEventReader(ring, ring.size());

            byte[] raw = concat(
                    opcode(Commands.Event.POINTER_UP, 0, 7,
                            Float.floatToRawIntBits(10.0f), Float.floatToRawIntBits(20.0f)),
                    opcode(Commands.Event.VALUE_CHANGED, 0, 5, Float.floatToRawIntBits(42.0f), 0),
                    opcode(Commands.Event.DATE_CHANGED, 0, 4, 19000, 3600000));

            List<Event> events = reader.decode(raw);
            assertEquals(3, events.size());

            Event up = events.get(0);
            assertTrue(up.isPointerUp());
            assertEquals(7, up.target());
            assertEquals(10.0f, up.x());
            assertEquals(20.0f, up.y());

            Event value = events.get(1);
            assertTrue(value.isValueChanged());
            assertEquals(5, value.target());
            assertEquals(42.0f, value.value());

            Event date = events.get(2);
            assertTrue(date.isDateChanged());
            assertEquals(4, date.target());
            assertEquals(19000, date.days());
            assertEquals(3600000, date.millisOfDay());
        }
    }

    @Test
    void resolvesTextChangedFromTheEventArena() {
        try (Memory ring = ringWithArena(HEADER_SIZE, "typed text", 0)) {
            RingEventReader reader = new RingEventReader(ring, ring.size());
            List<Event> events = reader.decode(
                    opcode(Commands.Event.TEXT_CHANGED, 0, 9, 0, 0));
            assertEquals(1, events.size());
            assertTrue(events.get(0).isTextChanged());
            assertEquals(9, events.get(0).target());
            assertEquals("typed text", events.get(0).text());
        }
    }

    @Test
    void distinguishesNavigateUrlFromBackRequest() {
        try (Memory ring = ringWithArena(HEADER_SIZE, "/kitchen", 0)) {
            RingEventReader reader = new RingEventReader(ring, ring.size());

            List<Event> url = reader.decode(
                    opcode(Commands.Event.NAVIGATE, Commands.Flags.NAVIGATE_URL, 0, 0, 0));
            assertEquals(1, url.size());
            assertTrue(url.get(0).isNavigate());
            assertEquals("/kitchen", url.get(0).url());

            List<Event> back = reader.decode(
                    opcode(Commands.Event.NAVIGATE, 0, 0, 0, 0));
            assertEquals(1, back.size());
            assertTrue(back.get(0).isNavigateBack());
            assertNull(back.get(0).url());
        }
    }

    @Test
    void ignoresUnknownCommandsAndNonEventCategories() {
        try (Memory ring = ringWithArena(HEADER_SIZE, "x", 0)) {
            RingEventReader reader = new RingEventReader(ring, ring.size());
            byte[] raw = new byte[OPCODE_SIZE]; // all zeros: category 0 (not EVENT)
            assertTrue(reader.decode(raw).isEmpty());

            byte[] unknown = opcode(0x7F, 0, 1, 0, 0); // EVENT category, unknown command
            assertTrue(reader.decode(unknown).isEmpty());
        }
    }

    @Test
    void returnsNullTextWhenArenaIsMissing() {
        RingEventReader reader = new RingEventReader(null, 0);
        List<Event> events = reader.decode(opcode(Commands.Event.TEXT_CHANGED, 0, 9, 0, 0));
        assertEquals(1, events.size());
        assertNotNull(events.get(0));
        assertNull(events.get(0).text());
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) {
            total += p.length;
        }
        byte[] out = new byte[total];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }
}