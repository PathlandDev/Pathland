package com.pathland.demo.gtk;

import com.pathland.view.Commands;
import com.pathland.view.transport.Event;
import com.sun.jna.Pointer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes raw {@code EVENT}-category opcodes drained from the shared ring
 * ({@code pathland_core_drain_events}) into {@link Event} objects, resolving
 * strings ({@code TEXT_CHANGED}, {@code NAVIGATE} URLs) from the shared
 * host → guest event arena. The wire is the opcode engine — never a
 * side-channel callback.
 */
public final class RingEventReader {

    private static final int OPCODE_SIZE = 16;
    private static final int CATEGORY_EVENT = 0x03;

    /** Ring header: byte offset of the event-arena offset (spec/OPCODE.md, memory.rs). */
    private static final int OFF_EVENT_ARENA_OFFSET = 0x40;

    private final Pointer ringPtr;
    private final long ringLen;
    private final int eventArenaOffset;

    public RingEventReader(Pointer ringPtr, long ringLen) {
        this.ringPtr = ringPtr;
        this.ringLen = ringLen;
        if (ringPtr != null && ringLen >= OFF_EVENT_ARENA_OFFSET + 4L) {
            this.eventArenaOffset = ringPtr.getInt(OFF_EVENT_ARENA_OFFSET);
        } else {
            this.eventArenaOffset = 0;
        }
    }

    /** Decode consecutive 16-byte opcodes from {@code raw} (a drain buffer). */
    public List<Event> decode(byte[] raw) {
        List<Event> events = new ArrayList<>();
        for (int off = 0; off + OPCODE_SIZE <= raw.length; off += OPCODE_SIZE) {
            if ((raw[off] & 0xFF) != CATEGORY_EVENT) {
                continue;
            }
            int command = raw[off + 1] & 0xFF;
            int flags = u16(raw, off + 2);
            int a = i32(raw, off + 4);
            int b = i32(raw, off + 8);
            int c = i32(raw, off + 12);
            Event event = decodeOne(command, flags, a, b, c);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    private Event decodeOne(int command, int flags, int a, int b, int c) {
        return switch (command) {
            case Commands.Event.POINTER_DOWN -> Event.pointerDown(a, f32(b), f32(c));
            case Commands.Event.POINTER_UP -> Event.pointerUp(a, f32(b), f32(c));
            case Commands.Event.POINTER_MOVE -> Event.pointerMove(a, f32(b), f32(c), flags);
            case Commands.Event.KEY_DOWN -> Event.keyDown(a, b, c, flags);
            case Commands.Event.KEY_UP -> Event.keyUp(a, b, c);
            case Commands.Event.VALUE_CHANGED -> Event.valueChanged(a, f32(b));
            case Commands.Event.TEXT_CHANGED -> Event.textChanged(a, arenaString(b));
            case Commands.Event.FOCUS_CHANGED -> Event.focusChanged(a, b != 0);
            case Commands.Event.EDITING_CHANGED -> Event.editingChanged(a, b != 0);
            case Commands.Event.SUBMIT -> Event.submit(a);
            case Commands.Event.SCROLL -> Event.scroll(a, f32(b), f32(c));
            case Commands.Event.WHEEL -> Event.wheel(a, f32(b), f32(c));
            case Commands.Event.DATE_CHANGED -> Event.dateChanged(a, b, c);
            case Commands.Event.NAVIGATE -> (flags & Commands.Flags.NAVIGATE_URL) != 0
                    ? Event.navigate(arenaString(b))
                    : Event.navigateBack();
            default -> null;
        };
    }

    /** Resolve a string from the shared event arena at {@code ref} ([u32 len][bytes]). */
    private String arenaString(int ref) {
        if (ref < 0 || ringPtr == null || eventArenaOffset <= 0) {
            return null;
        }
        long base = (long) eventArenaOffset + ref;
        if (base + 4 > ringLen) {
            return null;
        }
        int len = ringPtr.getInt(base);
        if (len < 0 || base + 4 + len > ringLen) {
            return null;
        }
        byte[] bytes = ringPtr.getByteArray(base + 4, len);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int u16(byte[] raw, int off) {
        return (raw[off] & 0xFF) | ((raw[off + 1] & 0xFF) << 8);
    }

    private static int i32(byte[] raw, int off) {
        return (raw[off] & 0xFF) | ((raw[off + 1] & 0xFF) << 8)
                | ((raw[off + 2] & 0xFF) << 16) | ((raw[off + 3] & 0xFF) << 24);
    }

    private static float f32(int bits) {
        return Float.intBitsToFloat(bits);
    }
}