package com.pathland.view.transport;

import com.pathland.view.Categories;
import com.pathland.view.Commands;
import com.pathland.view.emit.Frame;
import com.pathland.view.emit.Opcode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The network batch codec — mirrors {@code pathland_core_transport} in Java.
 *
 * <p>Batch wire format (both directions share the header):
 * <pre>
 * magic "PLPL" (u32 LE) | version (u16) | flags (u16) | frameCount (u32)
 * | opcodeCount (u32) | opcodes (16 B × count) | stringsLen (u32) | string bytes
 * </pre>
 *
 * <p>Frames are self-contained (relative string offsets, no mirrored arena), so each
 * WebSocket message is independent. Events flow host → guest with the
 * {@code HOST_TO_GUEST} direction flag; {@code TEXT_CHANGED} carries its text in the
 * string section referenced by a relative offset.
 */
public final class FrameCodec {

    public static final int MAGIC = 0x504C_504C;
    public static final int VERSION = 1;
    public static final int GUEST_TO_HOST = 0x0000;
    public static final int HOST_TO_GUEST = 0x0001;
    public static final int HEADER = 16;

    private FrameCodec() {}

    /** Encode a self-contained frame as a network batch. */
    public static byte[] encodeFrame(Frame frame) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeHeader(out, GUEST_TO_HOST, 0, frame.opcodes().size());
        for (Opcode op : frame.opcodes()) {
            writeBytes(out, op.toBytes());
        }
        writeIntLE(out, frame.strings().length);
        writeBytes(out, frame.strings());
        return out.toByteArray();
    }

    /** Decode a self-contained frame batch into opcodes + string section. */
    public static Frame decodeFrame(byte[] bytes) {
        Parsed parsed = parse(bytes);
        return new Frame(parsed.opcodes(), parsed.strings());
    }

    /** Encode a `META::RESYNC` request batch (host → guest): ask the guest for a full snapshot. */
    public static byte[] encodeResync() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeHeader(out, HOST_TO_GUEST, 0, 1);
        writeBytes(out, new Opcode(Categories.META, Commands.Meta.RESYNC, 0, 0, 0, 0).toBytes());
        writeIntLE(out, 0); // empty string section
        return out.toByteArray();
    }

    /** True when the batch is a `META::RESYNC` request (host → guest). */
    public static boolean isResync(byte[] bytes) {
        Parsed parsed = parse(bytes);
        for (Opcode op : parsed.opcodes()) {
            if (op.category() == Categories.META && op.command() == Commands.Meta.RESYNC) {
                return true;
            }
        }
        return false;
    }

    /** True when the batch carries `META::ENVIRONMENT` fields (host → guest). */
    public static boolean isEnvironment(byte[] bytes) {
        Parsed parsed = parse(bytes);
        for (Opcode op : parsed.opcodes()) {
            if (op.category() == Categories.META && op.command() == Commands.Meta.ENVIRONMENT) {
                return true;
            }
        }
        return false;
    }

    /** Decode a `META::ENVIRONMENT` field batch into {@link EnvironmentData}. */
    public static EnvironmentData decodeEnvironment(byte[] bytes) {
        Parsed parsed = parse(bytes);
        String route = null;
        float width = -1f;
        float height = -1f;
        for (Opcode op : parsed.opcodes()) {
            if (op.category() != Categories.META || op.command() != Commands.Meta.ENVIRONMENT) {
                continue;
            }
            switch (op.a() & 0xFFFF) {
                case Commands.Environment.VIEWPORT_WIDTH ->
                        width = Float.intBitsToFloat(op.b());
                case Commands.Environment.VIEWPORT_HEIGHT ->
                        height = Float.intBitsToFloat(op.b());
                case Commands.Environment.ROUTE ->
                        route = parsed.stringAt(op.b());
                default -> { }
            }
        }
        return new EnvironmentData(route, width, height);
    }

    /** Encode host → guest events as a batch (binary EVENT opcodes + string section). */
    public static byte[] encodeEvents(List<Event> events) {
        List<Opcode> opcodes = new ArrayList<>(events.size());
        ByteArrayOutputStream strings = new ByteArrayOutputStream();
        for (Event event : events) {
            if (event.isTextChanged()) {
                int offset = strings.size();
                byte[] utf8 = event.text().getBytes(StandardCharsets.UTF_8);
                writeIntLE(strings, utf8.length);
                strings.writeBytes(utf8);
                opcodes.add(new Opcode(
                        Categories.EVENT, Commands.Event.TEXT_CHANGED, 0, event.target(), offset, 0));
            } else if (event.isNavigate()) {
                if (event.isNavigateBack()) {
                    opcodes.add(new Opcode(Categories.EVENT, Commands.Event.NAVIGATE, 0, 0, 0, 0));
                } else {
                    int offset = strings.size();
                    byte[] utf8 = event.url().getBytes(StandardCharsets.UTF_8);
                    writeIntLE(strings, utf8.length);
                    strings.writeBytes(utf8);
                    opcodes.add(new Opcode(
                            Categories.EVENT, Commands.Event.NAVIGATE, Commands.Flags.NAVIGATE_URL, 0, offset, 0));
                }
            } else if (event.isValueChanged()) {
                opcodes.add(new Opcode(
                        Categories.EVENT,
                        Commands.Event.VALUE_CHANGED,
                        0,
                        event.target(),
                        Float.floatToRawIntBits(event.value()),
                        0));
            } else if (event.isKeyDown() || event.isKeyUp()) {
                opcodes.add(new Opcode(
                        Categories.EVENT, event.command(), event.flags(), event.target(), event.b(), event.c()));
            } else if (event.isDateChanged()) {
                opcodes.add(new Opcode(
                        Categories.EVENT, Commands.Event.DATE_CHANGED, 0, event.target(), event.b(), event.c()));
            } else if (event.isFocusChanged() || event.isEditingChanged() || event.isSubmit()) {
                opcodes.add(new Opcode(
                        Categories.EVENT, event.command(), 0, event.target(), event.b(), 0));
            } else {
                opcodes.add(new Opcode(
                        Categories.EVENT,
                        event.command(),
                        event.flags(),
                        event.target(),
                        Float.floatToRawIntBits(event.x()),
                        Float.floatToRawIntBits(event.y())));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeHeader(out, HOST_TO_GUEST, 0, opcodes.size());
        for (Opcode op : opcodes) {
            writeBytes(out, op.toBytes());
        }
        writeIntLE(out, strings.size());
        writeBytes(out, strings.toByteArray());
        return out.toByteArray();
    }

    /** Decode a host → guest event batch into typed events. */
    public static List<Event> decodeEvents(byte[] bytes) {
        Parsed parsed = parse(bytes);
        List<Event> events = new ArrayList<>();
        for (Opcode op : parsed.opcodes()) {
            if (op.category() != Categories.EVENT) {
                continue;
            }
            int command = op.command();
            switch (command) {
                case Commands.Event.TEXT_CHANGED -> events.add(Event.textChanged(op.a(), parsed.stringAt(op.b())));
                case Commands.Event.NAVIGATE -> {
                    if ((op.flags() & Commands.Flags.NAVIGATE_URL) != 0) {
                        events.add(Event.navigate(parsed.stringAt(op.b())));
                    } else {
                        events.add(Event.navigateBack());
                    }
                }
                case Commands.Event.VALUE_CHANGED -> events.add(Event.valueChanged(op.a(), Float.intBitsToFloat(op.b())));
                case Commands.Event.KEY_DOWN -> events.add(Event.keyDown(op.a(), op.b(), op.c(), op.flags()));
                case Commands.Event.KEY_UP -> events.add(Event.keyUp(op.a(), op.b(), op.c()));
                case Commands.Event.DATE_CHANGED -> events.add(Event.dateChanged(op.a(), op.b(), op.c()));
                case Commands.Event.FOCUS_CHANGED -> events.add(Event.focusChanged(op.a(), op.b() != 0));
                case Commands.Event.EDITING_CHANGED -> events.add(Event.editingChanged(op.a(), op.b() != 0));
                case Commands.Event.SUBMIT -> events.add(Event.submit(op.a()));
                case Commands.Event.SCROLL -> events.add(Event.scroll(
                        op.a(), Float.intBitsToFloat(op.b()), Float.intBitsToFloat(op.c())));
                case Commands.Event.WHEEL -> events.add(Event.wheel(
                        op.a(), Float.intBitsToFloat(op.b()), Float.intBitsToFloat(op.c())));
                case Commands.Event.MEDIA_PLAY_STATE_CHANGED -> events.add(
                        Event.mediaPlayStateChanged(op.a(), op.b() != 0));
                case Commands.Event.MEDIA_TIME_UPDATED -> events.add(
                        Event.mediaTimeUpdated(op.a(), Float.intBitsToFloat(op.b())));
                case Commands.Event.MEDIA_ENDED -> events.add(Event.mediaEnded(op.a()));
                case Commands.Event.MEDIA_VOLUME_CHANGED -> events.add(
                        Event.mediaVolumeChanged(op.a(), Float.intBitsToFloat(op.b())));
                case Commands.Event.POINTER_DOWN, Commands.Event.POINTER_MOVE, Commands.Event.POINTER_UP -> events.add(
                        new Event(command, op.a(), op.flags(), Float.intBitsToFloat(op.b()),
                                Float.intBitsToFloat(op.c()), 0, null, 0, 0));
                default -> { }
            }
        }
        return events;
    }

    private record Parsed(List<Opcode> opcodes, byte[] strings) {
        String stringAt(int offset) {
            int len = (strings[offset] & 0xFF)
                    | ((strings[offset + 1] & 0xFF) << 8)
                    | ((strings[offset + 2] & 0xFF) << 16)
                    | ((strings[offset + 3] & 0xFF) << 24);
            return new String(strings, offset + 4, len, StandardCharsets.UTF_8);
        }
    }

    private static Parsed parse(byte[] bytes) {
        if (bytes.length < HEADER) {
            throw new IllegalArgumentException("batch truncated");
        }
        int magic = readIntLE(bytes, 0);
        if (magic != MAGIC) {
            throw new IllegalArgumentException("bad batch magic 0x" + Integer.toHexString(magic));
        }
        int version = (bytes[4] & 0xFF) | ((bytes[5] & 0xFF) << 8);
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported batch version " + version);
        }
        int opcodeCount = readIntLE(bytes, 12);
        int opcodesEnd = HEADER + opcodeCount * Opcode.SIZE;
        if (opcodesEnd + 4 > bytes.length) {
            throw new IllegalArgumentException("batch truncated (opcodes)");
        }
        List<Opcode> opcodes = new ArrayList<>(opcodeCount);
        for (int i = 0; i < opcodeCount; i++) {
            byte[] slot = new byte[Opcode.SIZE];
            System.arraycopy(bytes, HEADER + i * Opcode.SIZE, slot, 0, Opcode.SIZE);
            opcodes.add(Opcode.fromBytes(slot));
        }
        int stringsLen = readIntLE(bytes, opcodesEnd);
        int stringsStart = opcodesEnd + 4;
        if (stringsStart + stringsLen > bytes.length) {
            throw new IllegalArgumentException("batch truncated (strings)");
        }
        byte[] strings = new byte[stringsLen];
        System.arraycopy(bytes, stringsStart, strings, 0, stringsLen);
        return new Parsed(opcodes, strings);
    }

    private static void writeHeader(ByteArrayOutputStream out, int flags, int frameCount, int opcodeCount) {
        writeIntLE(out, MAGIC);
        writeShortLE(out, VERSION);
        writeShortLE(out, flags);
        writeIntLE(out, frameCount);
        writeIntLE(out, opcodeCount);
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void writeShortLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private static void writeBytes(ByteArrayOutputStream out, byte[] bytes) {
        out.writeBytes(bytes);
    }

    private static int readIntLE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }
}