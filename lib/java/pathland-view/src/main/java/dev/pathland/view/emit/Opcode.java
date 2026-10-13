package dev.pathland.view.emit;

/**
 * A fixed-size 16-byte Pathland opcode (the wire format, little-endian):
 *
 * <pre>{@code
 * [Category u8][Command u8][Flags u16][A u32][B u32][C u32]
 * }</pre>
 *
 * <p>Matches {@code pathland_core::Opcode} and {@code spec/OPCODE.md}. Exactly 16
 * bytes, so a 64-byte cache line holds 4 opcodes.
 */
public record Opcode(int category, int command, int flags, int a, int b, int c) {

    public static final int SIZE = 16;

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
}