package com.pathland.view.ffm;

import com.pathland.view.Button;
import com.pathland.view.Categories;
import com.pathland.view.Commands;
import com.pathland.view.Environment;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.emit.Emitter;
import com.pathland.view.emit.Opcode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the desktop/embedded path: the Java DSL pushes raw 16-byte opcodes into the
 * Rust SPSC ring via JNA (zero JNI, zero-copy). Skipped when {@code libpathland_core}
 * is not on {@code java.library.path}.
 */
class RingOpcodeSinkTest {

    private static final int OFF_RING_OFFSET = 0x06;
    private static final int OFF_SLOT_COUNT = 0x0E;
    private static final int OFF_WRITE_CURSOR = 0x22;

    @Test
    void pushesOpcodesIntoTheSharedRing() {
        PathlandCore core;
        try {
            core = PathlandCore.instance();
        } catch (Throwable t) {
            Assumptions.assumeTrue(false,
                    "libpathland_core unavailable on java.library.path; JNA ring test skipped: " + t);
            return;
        }

        try (RingOpcodeSink sink = new RingOpcodeSink(core)) {
            Emitter emitter = new Emitter(sink);
            emitter.mount(
                    VStack.of(Text.of("Hi"), Button.of("Go", () -> { })),
                    Environment.DEFAULT);

            long len = core.ringLen(sink.handle());
            byte[] buf = core.ringPtr(sink.handle()).getByteArray(0, (int) len);
            assertTrue(buf.length >= 64, "full linear memory buffer (header + rings + arena)");

            int ringOff = readIntLE(buf, OFF_RING_OFFSET);
            int slotCount = readIntLE(buf, OFF_SLOT_COUNT);
            int writeCursor = readIntLE(buf, OFF_WRITE_CURSOR);

            boolean sawCreate = false;
            boolean sawSetText = false;
            for (int i = 0; i < writeCursor; i++) {
                int slot = i % slotCount;
                int base = ringOff + slot * 16;
                Opcode op = Opcode.fromBytes(java.util.Arrays.copyOfRange(buf, base, base + 16));
                if (op.category() == Categories.TREE && op.command() == Commands.Tree.CREATE_NODE) {
                    sawCreate = true;
                }
                if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_TEXT) {
                    sawSetText = true;
                }
            }
            assertTrue(sawCreate, "CREATE_NODE written into the ring");
            assertTrue(sawSetText, "SET_TEXT written into the ring");

            // The SET_TEXT string ("Hi") lives in the shared arena (absolute offset).
            int arenaOff = readIntLE(buf, 0x12);
            String text = new String(buf, arenaOff + 4, 2);
            assertEquals("Hi", text);
        }
    }

    @Test
    void emptyTextAllocatesAValidZeroLengthArenaEntry() {
        PathlandCore core;
        try {
            core = PathlandCore.instance();
        } catch (Throwable t) {
            Assumptions.assumeTrue(false,
                    "libpathland_core unavailable on java.library.path; JNA ring test skipped: " + t);
            return;
        }

        try (RingOpcodeSink sink = new RingOpcodeSink(core)) {
            // The ring path must not crash on an empty SET_TEXT (JNA Memory(0)); the
            // arena entry is [u32 len=0][0 bytes] and the opcode references it.
            sink.beginFrame();
            sink.setText(3, "");
            sink.endFrame();

            long len = core.ringLen(sink.handle());
            byte[] buf = core.ringPtr(sink.handle()).getByteArray(0, (int) len);

            int arenaOff = readIntLE(buf, 0x12);
            int ref = readIntLE(buf, arenaOff);
            int entryLen = readIntLE(buf, arenaOff + ref);
            assertEquals(0, entryLen, "empty string arena entry has len 0");
        }
    }

    private static int readIntLE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF)
                | ((buf[offset + 1] & 0xFF) << 8)
                | ((buf[offset + 2] & 0xFF) << 16)
                | ((buf[offset + 3] & 0xFF) << 24);
    }
}