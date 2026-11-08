package dev.pathland.core.transport;

import dev.pathland.core.protocol.Categories;
import dev.pathland.core.protocol.Commands;
import dev.pathland.core.protocol.Opcode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The network batch codec — mirrors {@code pathland_core_transport}. */
public final class FrameCodec {
    public static final int MAGIC = 0x504C_504C;
    public static final int VERSION = 1;
    public static final int GUEST_TO_HOST = 0x0000;
    public static final int HOST_TO_GUEST = 0x0001;
    public static final int HEADER = 16;

    private FrameCodec() {
    }

    /** Encode a self-contained frame as a network batch (sequence 0). */
    public static byte[] encodeFrame(ProtocolFrame frame) {
        return encodeFrame(frame, 0);
    }

    /** Encode a self-contained frame as a guest to host batch with {@code sequence}. */
    public static byte[] encodeFrame(ProtocolFrame frame, int sequence) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeHeader(out, GUEST_TO_HOST, sequence, frame.opcodes().size());
        for (Opcode op : frame.opcodes()) {
            writeBytes(out, op.toBytes());
        }
        writeIntLE(out, frame.strings().length);
        writeBytes(out, frame.strings());
        return out.toByteArray();
    }

    /** Decode a self-contained frame batch into opcodes and string section. */
    public static ProtocolFrame decodeFrame(byte[] bytes) {
        Parsed parsed = parse(bytes);
        return new ProtocolFrame(parsed.opcodes, parsed.strings);
    }

    /** The per-stream message sequence encoded in a batch header (offset 8). */
    public static int sequence(byte[] bytes) {
        if (bytes.length < HEADER) {
            throw new IllegalArgumentException("batch truncated");
        }
        return readIntLE(bytes, 8);
    }

    /** Encode a {@code META::RESYNC} request batch (host to guest). */
    public static byte[] encodeResync() {
        return singleMeta(Commands.Meta.RESYNC);
    }

    /** Encode a {@code META::PONG} reply batch (host to guest). */
    public static byte[] encodePong() {
        return singleMeta(Commands.Meta.PONG);
    }

    /** Encode a {@code META::PING} heartbeat probe batch (guest to host). */
    public static byte[] encodePing() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeHeader(out, GUEST_TO_HOST, 0, 1);
        writeBytes(out, new Opcode(Categories.META, Commands.Meta.PING, 0, 0, 0, 0).toBytes());
        writeIntLE(out, 0);
        return out.toByteArray();
    }

    private static byte[] singleMeta(int command) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeHeader(out, HOST_TO_GUEST, 0, 1);
        writeBytes(out, new Opcode(Categories.META, command, 0, 0, 0, 0).toBytes());
        writeIntLE(out, 0);
        return out.toByteArray();
    }

    /** Encode a {@code META::ENVIRONMENT} field batch (viewport and route). */
    public static byte[] encodeEnvironment(EnvironmentData env) {
        byte[] route = env.route().getBytes(StandardCharsets.UTF_8);
        List<Opcode> ops = new ArrayList<>(3);
        if (env.viewportWidth() > 0) {
            ops.add(new Opcode(Categories.META, Commands.Meta.ENVIRONMENT, 0,
                    Commands.Environment.VIEWPORT_WIDTH,
                    Float.floatToRawIntBits(env.viewportWidth()), 0));
        }
        if (env.viewportHeight() > 0) {
            ops.add(new Opcode(Categories.META, Commands.Meta.ENVIRONMENT, 0,
                    Commands.Environment.VIEWPORT_HEIGHT,
                    Float.floatToRawIntBits(env.viewportHeight()), 0));
        }
        ops.add(new Opcode(Categories.META, Commands.Meta.ENVIRONMENT, 0,
                Commands.Environment.ROUTE, 0, 0));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeHeader(out, HOST_TO_GUEST, 0, ops.size());
        for (Opcode op : ops) {
            writeBytes(out, op.toBytes());
        }
        writeIntLE(out, 4 + route.length);
        writeIntLE(out, route.length);
        out.writeBytes(route);
        return out.toByteArray();
    }

    /** True when the batch is a {@code META::RESYNC} request. */
    public static boolean isResync(byte[] bytes) {
        return parse(bytes).opcodes.stream().anyMatch(
                op -> op.category() == Categories.META
                        && op.command() == Commands.Meta.RESYNC);
    }

    /** True when the batch carries a {@code META::PING} heartbeat probe. */
    public static boolean isPing(byte[] bytes) {
        return parse(bytes).opcodes.stream().anyMatch(
                op -> op.category() == Categories.META
                        && op.command() == Commands.Meta.PING);
    }

    /** True when the batch carries {@code META::ENVIRONMENT} fields. */
    public static boolean isEnvironment(byte[] bytes) {
        return parse(bytes).opcodes.stream().anyMatch(
                op -> op.category() == Categories.META
                        && op.command() == Commands.Meta.ENVIRONMENT);
    }

    /** Decode a {@code META::ENVIRONMENT} field batch into {@link EnvironmentData}. */
    public static EnvironmentData decodeEnvironment(byte[] bytes) {
        Parsed parsed = parse(bytes);
        String route = null;
        float width = -1f;
        float height = -1f;
        for (Opcode op : parsed.opcodes) {
            if (op.category() != Categories.META
                    || op.command() != Commands.Meta.ENVIRONMENT) {
                continue;
            }
            switch (op.a() & 0xFFFF) {
                case Commands.Environment.VIEWPORT_WIDTH:
                    width = Float.intBitsToFloat(op.b());
                    break;
                case Commands.Environment.VIEWPORT_HEIGHT:
                    height = Float.intBitsToFloat(op.b());
                    break;
                case Commands.Environment.ROUTE:
                    route = parsed.stringAt(op.b());
                    break;
                default:
                    break;
            }
        }
        return new EnvironmentData(route, width, height);
    }

    /** Encode host to guest events as a batch. */
    public static byte[] encodeEvents(List<Event> events) {
        List<Opcode> opcodes = new ArrayList<>(events.size());
        ByteArrayOutputStream strings = new ByteArrayOutputStream();
        for (Event event : events) {
            if (event.isTextChanged()) {
                int offset = strings.size();
                byte[] utf8 = (event.text() == null ? "" : event.text())
                        .getBytes(StandardCharsets.UTF_8);
                writeIntLE(strings, utf8.length);
                strings.writeBytes(utf8);
                opcodes.add(new Opcode(
                        Categories.EVENT, Commands.Event.TEXT_CHANGED, 0, event.target(), offset, 0));
            } else if (event.isNavigate()) {
                if (event.isNavigateBack()) {
                    opcodes.add(new Opcode(Categories.EVENT, Commands.Event.NAVIGATE, 0, 0, 0, 0));
                } else {
                    int offset = strings.size();
                    byte[] utf8 = (event.url() == null ? "" : event.url())
                            .getBytes(StandardCharsets.UTF_8);
                    writeIntLE(strings, utf8.length);
                    strings.writeBytes(utf8);
                    opcodes.add(new Opcode(Categories.EVENT, Commands.Event.NAVIGATE,
                            Commands.Flags.NAVIGATE_URL, 0, offset, 0));
                }
            } else if (event.isValueChanged()) {
                opcodes.add(new Opcode(Categories.EVENT, Commands.Event.VALUE_CHANGED, 0,
                        event.target(), Float.floatToRawIntBits(event.value()), 0));
            } else if (event.isKeyDown() || event.isKeyUp()) {
                opcodes.add(new Opcode(Categories.EVENT, event.command(), event.flags(),
                        event.target(), event.b(), event.c()));
            } else if (event.isDateChanged()) {
                opcodes.add(new Opcode(Categories.EVENT, Commands.Event.DATE_CHANGED, 0,
                        event.target(), event.b(), event.c()));
            } else if (event.isFocusChanged()
                    || event.isEditingChanged()
                    || event.isSubmit()) {
                opcodes.add(new Opcode(Categories.EVENT, event.command(), 0,
                        event.target(), event.b(), 0));
            } else if (event.isMediaTimeUpdated()
                    || event.isMediaVolumeChanged()
                    || event.isMediaDurationChanged()
                    || event.isFitChanged()) {
                opcodes.add(new Opcode(Categories.EVENT, event.command(), 0,
                        event.target(), Float.floatToRawIntBits(event.value()), 0));
            } else if (event.isMediaPlayStateChanged() || event.isMediaEnded()) {
                opcodes.add(new Opcode(Categories.EVENT, event.command(), 0,
                        event.target(), event.b(), 0));
            } else {
                opcodes.add(new Opcode(Categories.EVENT, event.command(), event.flags(),
                        event.target(), Float.floatToRawIntBits(event.x()),
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

    /** Decode a host to guest event batch into typed events. */
    public static List<Event> decodeEvents(byte[] bytes) {
        Parsed parsed = parse(bytes);
        List<Event> events = new ArrayList<>();
        for (Opcode op : parsed.opcodes) {
            if (op.category() != Categories.EVENT) {
                continue;
            }
            switch (op.command()) {
                case Commands.Event.TEXT_CHANGED:
                    events.add(Event.textChanged(op.a(), parsed.stringAt(op.b())));
                    break;
                case Commands.Event.NAVIGATE:
                    events.add((op.flags() & Commands.Flags.NAVIGATE_URL) != 0
                            ? Event.navigate(parsed.stringAt(op.b()))
                            : Event.navigateBack());
                    break;
                case Commands.Event.VALUE_CHANGED:
                    events.add(Event.valueChanged(op.a(), Float.intBitsToFloat(op.b())));
                    break;
                case Commands.Event.KEY_DOWN:
                    events.add(Event.keyDown(op.a(), op.b(), op.c(), op.flags()));
                    break;
                case Commands.Event.KEY_UP:
                    events.add(Event.keyUp(op.a(), op.b(), op.c()));
                    break;
                case Commands.Event.DATE_CHANGED:
                    events.add(Event.dateChanged(op.a(), op.b(), op.c()));
                    break;
                case Commands.Event.FOCUS_CHANGED:
                    events.add(Event.focusChanged(op.a(), op.b() != 0));
                    break;
                case Commands.Event.EDITING_CHANGED:
                    events.add(Event.editingChanged(op.a(), op.b() != 0));
                    break;
                case Commands.Event.SUBMIT:
                    events.add(Event.submit(op.a()));
                    break;
                case Commands.Event.SCROLL:
                    events.add(Event.scroll(op.a(), Float.intBitsToFloat(op.b()),
                            Float.intBitsToFloat(op.c())));
                    break;
                case Commands.Event.WHEEL:
                    events.add(Event.wheel(op.a(), Float.intBitsToFloat(op.b()),
                            Float.intBitsToFloat(op.c())));
                    break;
                case Commands.Event.MEDIA_PLAY_STATE_CHANGED:
                    events.add(Event.mediaPlayStateChanged(op.a(), op.b() != 0));
                    break;
                case Commands.Event.MEDIA_TIME_UPDATED:
                    events.add(Event.mediaTimeUpdated(op.a(), Float.intBitsToFloat(op.b())));
                    break;
                case Commands.Event.MEDIA_ENDED:
                    events.add(Event.mediaEnded(op.a()));
                    break;
                case Commands.Event.MEDIA_VOLUME_CHANGED:
                    events.add(Event.mediaVolumeChanged(op.a(), Float.intBitsToFloat(op.b())));
                    break;
                case Commands.Event.MEDIA_DURATION_CHANGED:
                    events.add(Event.mediaDurationChanged(op.a(), Float.intBitsToFloat(op.b())));
                    break;
                case Commands.Event.FIT_CHANGED:
                    events.add(Event.fitChanged(op.a(), Float.intBitsToFloat(op.b())));
                    break;
                case Commands.Event.POINTER_DOWN:
                case Commands.Event.POINTER_MOVE:
                case Commands.Event.POINTER_UP:
                    events.add(new Event(op.command(), op.a(), op.flags(),
                            Float.intBitsToFloat(op.b()), Float.intBitsToFloat(op.c()),
                            0f, null, 0, 0));
                    break;
                default:
                    break;
            }
        }
        return events;
    }

    private static final class Parsed {
        final List<Opcode> opcodes;
        final byte[] strings;

        Parsed(List<Opcode> opcodes, byte[] strings) {
            this.opcodes = opcodes;
            this.strings = strings;
        }

        String stringAt(int offset) {
            if (offset < 0 || (long) offset + 4L > (long) strings.length) {
                throw new IllegalArgumentException("string offset out of bounds");
            }
            long len = readUInt32LE(strings, offset);
            long start = (long) offset + 4L;
            if (start + len > (long) strings.length) {
                throw new IllegalArgumentException("string length out of bounds");
            }
            return new String(strings, (int) start, (int) len, StandardCharsets.UTF_8);
        }
    }

    private static Parsed parse(byte[] bytes) {
        if (bytes.length < HEADER) {
            throw new IllegalArgumentException("batch truncated");
        }
        int magic = readIntLE(bytes, 0);
        if (magic != MAGIC) {
            throw new IllegalArgumentException(
                    "bad batch magic 0x" + Integer.toHexString(magic));
        }
        int version = (bytes[4] & 0xFF) | ((bytes[5] & 0xFF) << 8);
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported batch version " + version);
        }
        // The opcode count is an attacker-controlled u32. Compute with Long and
        // validate against the actual message length BEFORE allocating, so a
        // declared count cannot overflow Int arithmetic into a small end offset
        // and then trigger a huge ArrayList allocation (OOM).
        long opcodeCount = readUInt32LE(bytes, 12);
        long opcodesEnd = (long) HEADER + opcodeCount * (long) Opcode.SIZE;
        if (opcodesEnd + 4L > (long) bytes.length) {
            throw new IllegalArgumentException("batch truncated (opcodes)");
        }
        if (opcodeCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("opcode count out of range");
        }
        int opcodeCountInt = (int) opcodeCount;
        List<Opcode> opcodes = new ArrayList<>(opcodeCountInt);
        for (int i = 0; i < opcodeCountInt; i++) {
            byte[] slot = new byte[Opcode.SIZE];
            System.arraycopy(bytes, HEADER + i * Opcode.SIZE, slot, 0, Opcode.SIZE);
            opcodes.add(Opcode.fromBytes(slot));
        }
        long stringsLen = readUInt32LE(bytes, (int) opcodesEnd);
        long stringsStart = opcodesEnd + 4L;
        if (stringsStart + stringsLen > (long) bytes.length) {
            throw new IllegalArgumentException("batch truncated (strings)");
        }
        if (stringsLen > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("string section out of range");
        }
        int stringsLenInt = (int) stringsLen;
        byte[] strings = new byte[stringsLenInt];
        System.arraycopy(bytes, (int) stringsStart, strings, 0, stringsLenInt);
        return new Parsed(opcodes, strings);
    }

    private static void writeHeader(
            ByteArrayOutputStream out, int flags, int sequence, int opcodeCount) {
        writeIntLE(out, MAGIC);
        writeShortLE(out, VERSION);
        writeShortLE(out, flags);
        writeIntLE(out, sequence);
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

    /** Read a little-endian u32 as a non-negative Long (no sign/overflow surprises). */
    private static long readUInt32LE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL)
                | ((bytes[offset + 1] & 0xFFL) << 8)
                | ((bytes[offset + 2] & 0xFFL) << 16)
                | ((bytes[offset + 3] & 0xFFL) << 24);
    }
}
