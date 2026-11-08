package dev.pathland.core.protocol;

import java.util.Objects;

/**
 * A fixed-size 16-byte Pathland opcode (little-endian):
 *
 * <p>{@code [Category u8][Command u8][Flags u16][A u32][B u32][C u32]}
 *
 * <p>The component-named methods preserve the Java record accessor API
 * ({@code op.category()}, {@code op.b()}, ...) used by the renderers, hosts, and
 * tests. Conventional getters are also provided for Kotlin property syntax.
 */
public final class Opcode {
    public static final int SIZE = 16;

    private final int category;
    private final int command;
    private final int flags;
    private final int a;
    private final int b;
    private final int c;

    public Opcode(int category, int command, int flags, int a, int b, int c) {
        this.category = category;
        this.command = command;
        this.flags = flags;
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public int category() {
        return category;
    }

    public int command() {
        return command;
    }

    public int flags() {
        return flags;
    }

    public int a() {
        return a;
    }

    public int b() {
        return b;
    }

    public int c() {
        return c;
    }

    public int getCategory() {
        return category;
    }

    public int getCommand() {
        return command;
    }

    public int getFlags() {
        return flags;
    }

    public int getA() {
        return a;
    }

    public int getB() {
        return b;
    }

    public int getC() {
        return c;
    }

    /** Serialize to the 16-byte little-endian wire representation. */
    public byte[] toBytes() {
        byte[] out = new byte[SIZE];
        out[0] = (byte) category;
        out[1] = (byte) command;
        out[2] = (byte) flags;
        out[3] = (byte) (flags >>> 8);
        writeIntLE(out, 4, a);
        writeIntLE(out, 8, b);
        writeIntLE(out, 12, c);
        return out;
    }

    /** Deserialize from the 16-byte little-endian wire representation. */
    public static Opcode fromBytes(byte[] bytes) {
        if (bytes.length != SIZE) {
            throw new IllegalArgumentException("opcode must be " + SIZE + " bytes");
        }
        return new Opcode(
                bytes[0] & 0xFF,
                bytes[1] & 0xFF,
                (bytes[2] & 0xFF) | ((bytes[3] & 0xFF) << 8),
                readIntLE(bytes, 4),
                readIntLE(bytes, 8),
                readIntLE(bytes, 12));
    }

    private static void writeIntLE(byte[] out, int offset, int value) {
        out[offset] = (byte) value;
        out[offset + 1] = (byte) (value >>> 8);
        out[offset + 2] = (byte) (value >>> 16);
        out[offset + 3] = (byte) (value >>> 24);
    }

    private static int readIntLE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Opcode)) {
            return false;
        }
        Opcode that = (Opcode) other;
        return category == that.category
                && command == that.command
                && flags == that.flags
                && a == that.a
                && b == that.b
                && c == that.c;
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, command, flags, a, b, c);
    }

    @Override
    public String toString() {
        return "Opcode(category=" + category + ", command=" + command + ", flags=" + flags
                + ", a=" + a + ", b=" + b + ", c=" + c + ")";
    }
}
