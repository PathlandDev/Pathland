package com.pathland.view.emit;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A self-contained frame: a batch of 16-byte opcodes plus its own string section.
 * {@code SET_TEXT} (and {@code STRING}-typed {@code SET_PROPERTY}) reference strings
 * by a *relative* offset into this section, so a frame decodes with no prior state.
 * This is the transport seam used by the HTML renderer and the WebSocket codec.
 */
public record ProtocolFrame(List<Opcode> opcodes, byte[] strings) {

    public static final ProtocolFrame EMPTY = new ProtocolFrame(List.of(), new byte[0]);

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
}