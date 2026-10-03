package com.pathland.server;

import com.pathland.view.Categories;
import com.pathland.view.Commands;
import com.pathland.view.ValueTypes;
import com.pathland.view.emit.Frame;
import com.pathland.view.emit.Opcode;
import com.pathland.view.transport.FrameCodec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The session delta batcher: merges per-signal frames into one network batch
 * (the Java analogue of the Rust transport's {@code Batcher}), rebasing string
 * offsets, so a continuous input burst collapses into far fewer WebSocket
 * messages instead of overflowing the send queue.
 */
class DeltaBatcherTest {

    private static final int PROP_VALUE = 0x2006;

    @Test
    void mergesFramesIntoOneBatchWithRebasedStrings() {
        List<byte[]> sent = new ArrayList<>();
        // A long flush interval + manual flush keeps the test deterministic.
        DeltaBatcher batcher = new DeltaBatcher(60_000, 1_000_000, sent::add);

        // Frame 1: SET_TEXT "ab" (offset 0) + a numeric VALUE change.
        byte[] strings1 = stringSection("ab");
        Frame f1 = new Frame(List.of(
                new Opcode(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 1, 0, 0),
                new Opcode(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, 1,
                        (ValueTypes.F32 << 16) | PROP_VALUE, Float.floatToIntBits(0.5f))),
                strings1);
        // Frame 2: SET_TEXT "cd" (its relative offset 0 must rebase past frame 1's section).
        byte[] strings2 = stringSection("cd");
        Frame f2 = new Frame(List.of(
                new Opcode(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 2, 0, 0)),
                strings2);

        batcher.append(f1);
        batcher.append(f2);
        batcher.flush();
        batcher.close();

        assertEquals(1, sent.size(), "all frames flush as a single batch");
        Frame merged = FrameCodec.decodeFrame(sent.get(0));
        assertEquals(3, merged.opcodes().size());
        // Frame 1's SET_TEXT offset stays 0 ("ab"); frame 2's is rebased to 6.
        assertEquals("ab", merged.stringAt(0));
        assertEquals("cd", merged.stringAt(6));
    }

    @Test
    void collapseABurstIntoASingleSend() {
        List<byte[]> sent = new ArrayList<>();
        DeltaBatcher batcher = new DeltaBatcher(60_000, 1_000_000, sent::add);

        // A 100-frame drag burst (numeric VALUE deltas only).
        for (int i = 0; i < 100; i++) {
            batcher.append(new Frame(List.of(
                    new Opcode(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, 1,
                            (ValueTypes.F32 << 16) | PROP_VALUE, Float.floatToIntBits(i / 100.0f))),
                    new byte[0]));
        }
        batcher.flush();
        batcher.close();

        assertEquals(1, sent.size(), "a drag burst collapses to a single message");
        Frame merged = FrameCodec.decodeFrame(sent.get(0));
        assertEquals(100, merged.opcodes().size());
    }

    @Test
    void stampsIncrementingSequencesAndSkipsEmptyFlushes() {
        List<byte[]> sent = new ArrayList<>();
        DeltaBatcher batcher = new DeltaBatcher(60_000, 1_000_000, sent::add);

        batcher.flush(); // nothing pending: must not consume a sequence number
        batcher.append(new Frame(List.of(new Opcode(
                Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 1, 0, 0)), new byte[0]));
        batcher.flush();
        batcher.append(new Frame(List.of(new Opcode(
                Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 2, 0, 0)), new byte[0]));
        batcher.flush();
        batcher.close();

        assertEquals(2, sent.size());
        assertEquals(1, FrameCodec.sequence(sent.get(0)), "first batch sequence");
        assertEquals(2, FrameCodec.sequence(sent.get(1)), "second batch sequence");
    }

    @Test
    void blockingSenderDoesNotHoldTheBatcherLock() throws Exception {
        // The sender runs OUTSIDE the monitor, so a blocking send (a slow/half-dead
        // client) can never stall the actor thread's append.
        List<byte[]> sent = new ArrayList<>();
        CountDownLatch senderEntered = new CountDownLatch(1);
        DeltaBatcher batcher = new DeltaBatcher(60_000, 1_000_000, bytes -> {
            sent.add(bytes);
            senderEntered.countDown();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        batcher.append(new Frame(List.of(new Opcode(
                Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 1, 0, 0)), new byte[0]));
        Thread flusher = new Thread(batcher::flush);
        flusher.start();
        assertTrue(senderEntered.await(2, TimeUnit.SECONDS), "sender entered and is blocking");

        long t0 = System.nanoTime();
        batcher.append(new Frame(List.of(new Opcode(
                Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 2, 0, 0)), new byte[0]));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(elapsedMs < 100, "append blocked behind a blocking sender (" + elapsedMs + " ms)");

        flusher.join();
        batcher.close();
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

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }
}