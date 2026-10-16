package dev.pathland.server;

import dev.pathland.view.Categories;
import dev.pathland.view.Commands;
import dev.pathland.view.ValueTypes;
import dev.pathland.view.emit.Opcode;
import dev.pathland.view.emit.ProtocolFrame;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The frame merger (spec/PROXY.md §5): appending a proxy's origin snapshot to an
 * SSR frame, rebasing its string offsets so the renderer emits one document.
 */
class FrameMergerTest {

    @Test
    void appendsExtrasAndRebasesTheirStringOffsets() {
        ProtocolFrame base = new ProtocolFrame(List.of(
                new Opcode(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 1, 0, 0)),
                stringSection("a"));
        ProtocolFrame extra = new ProtocolFrame(List.of(
                new Opcode(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 9, 0, 0)),
                stringSection("b"));

        ProtocolFrame merged = FrameMerger.merge(base, List.of(extra));

        assertEquals(2, merged.opcodes().size());
        assertEquals(0, merged.opcodes().get(0).b(), "the base offset is unchanged");
        assertEquals(5, merged.opcodes().get(1).b(), "the extra's offset rebases past the base section");
        assertEquals("a", merged.stringAt(0));
        assertEquals("b", merged.stringAt(5));
    }

    @Test
    void rebasesEveryStringBearingOperand() {
        ProtocolFrame base = new ProtocolFrame(List.of(
                new Opcode(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 1, 0, 0)),
                stringSection("abc")); // 4 + 3 = 7 bytes
        ProtocolFrame extra = new ProtocolFrame(List.of(
                // LIST (FIT_QUERY) at relative offset 0
                new Opcode(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, 9,
                        (ValueTypes.LIST << 16) | 0x1039, 0),
                // STRING property at relative offset 12 (after the 12-byte list entry)
                new Opcode(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, 9,
                        (ValueTypes.STRING << 16) | 0x1002, 12)),
                concat(listSection(0f, 640f), stringSection("x")));

        ProtocolFrame merged = FrameMerger.merge(base, List.of(extra));

        assertEquals(7, merged.opcodes().get(1).c(), "the LIST offset rebases");
        assertEquals(7 + 12, merged.opcodes().get(2).c(), "the STRING offset rebases");
    }

    @Test
    void emptyOrNullExtrasReturnTheBaseUnchanged() {
        ProtocolFrame base = new ProtocolFrame(List.of(
                new Opcode(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 1, 0, 0)),
                stringSection("a"));
        assertSame(base, FrameMerger.merge(base, List.of()));
        assertSame(base, FrameMerger.merge(base, null));
        assertSame(base, FrameMerger.merge(base, List.of(ProtocolFrame.EMPTY)));
    }

    private static byte[] stringSection(String... entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String entry : entries) {
            byte[] bytes = entry.getBytes(StandardCharsets.UTF_8);
            writeIntLE(out, bytes.length);
            out.writeBytes(bytes);
        }
        return out.toByteArray();
    }

    private static byte[] listSection(float... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeIntLE(out, values.length);
        for (float v : values) {
            writeIntLE(out, Float.floatToIntBits(v));
        }
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }
}
