package dev.pathland.core.transport;

import dev.pathland.core.protocol.Opcode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A self-contained frame: 16-byte opcodes plus its own string section. STRING
 * payloads reference strings by a relative offset, so a frame decodes with no
 * prior state.
 */
public final class ProtocolFrame {
    public static final ProtocolFrame EMPTY =
            new ProtocolFrame(Collections.emptyList(), new byte[0]);

    private final List<Opcode> opcodes;
    private final byte[] strings;

    public ProtocolFrame(List<Opcode> opcodes, byte[] strings) {
        this.opcodes = opcodes;
        this.strings = strings;
    }

    public List<Opcode> opcodes() {
        return opcodes;
    }

    public byte[] strings() {
        return strings;
    }

    public List<Opcode> getOpcodes() {
        return opcodes;
    }

    public byte[] getStrings() {
        return strings;
    }

    public boolean isEmpty() {
        return opcodes.isEmpty();
    }

    /** Read a length-prefixed string at a relative offset in the string section. */
    public String stringAt(int offset) {
        int len = readIntLE(strings, offset);
        return new String(strings, offset + 4, len, StandardCharsets.UTF_8);
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
        if (!(other instanceof ProtocolFrame)) {
            return false;
        }
        ProtocolFrame that = (ProtocolFrame) other;
        return opcodes.equals(that.opcodes) && Arrays.equals(strings, that.strings);
    }

    @Override
    public int hashCode() {
        return 31 * opcodes.hashCode() + Arrays.hashCode(strings);
    }

    @Override
    public String toString() {
        return "ProtocolFrame(opcodes=" + opcodes + ", strings=" + strings.length + " bytes)";
    }
}
