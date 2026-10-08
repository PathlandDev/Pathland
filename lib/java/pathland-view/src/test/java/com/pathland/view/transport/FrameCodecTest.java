package com.pathland.view.transport;

import com.pathland.view.Categories;
import com.pathland.view.Commands;
import com.pathland.view.emit.ProtocolFrame;
import com.pathland.view.emit.Opcode;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The network batch codec's transport-liveness helpers: {@code META::PING}
 * detection and the {@code META::PONG} reply (spec/OPCODE.md §Transport
 * heartbeat — network batch transport only).
 */
class FrameCodecTest {

    @Test
    void encodePongProducesASingleMetaPongBatch() {
        byte[] bytes = FrameCodec.encodePong();
        ProtocolFrame frame = FrameCodec.decodeFrame(bytes);
        assertEquals(1, frame.opcodes().size());
        Opcode op = frame.opcodes().get(0);
        assertEquals(Categories.META, op.category());
        assertEquals(Commands.Meta.PONG, op.command());
        assertEquals(0, op.a());
        assertEquals(0, op.b());
        assertEquals(0, op.c());
    }

    @Test
    void encodePingProducesASingleMetaPingBatch() {
        byte[] bytes = FrameCodec.encodePing();
        assertTrue(FrameCodec.isPing(bytes), "encodePing is detected as a PING");
        ProtocolFrame frame = FrameCodec.decodeFrame(bytes);
        Opcode op = frame.opcodes().get(0);
        assertEquals(Categories.META, op.category());
        assertEquals(Commands.Meta.PING, op.command());
    }

    @Test
    void encodeEnvironmentRoundTripsViewportAndRoute() {
        byte[] bytes = FrameCodec.encodeEnvironment(new EnvironmentData("/kitchen", 1280f, 800f));
        assertTrue(FrameCodec.isEnvironment(bytes), "encodeEnvironment is detected as an environment batch");
        EnvironmentData env = FrameCodec.decodeEnvironment(bytes);
        assertEquals("/kitchen", env.route());
        assertEquals(1280f, env.viewportWidth());
        assertEquals(800f, env.viewportHeight());
    }

    @Test
    void encodeEnvironmentWithUnknownViewportEmitsOnlyTheRouteField() {
        byte[] bytes = FrameCodec.encodeEnvironment(EnvironmentData.of("/"));
        ProtocolFrame frame = FrameCodec.decodeFrame(bytes);
        assertEquals(1, frame.opcodes().size(), "no viewport fields when the viewport is unknown");
        assertEquals(Commands.Environment.ROUTE, frame.opcodes().get(0).a() & 0xFFFF);
    }

    @Test
    void encodeFrameStampsAndReadsTheMessageSequence() {
        ProtocolFrame frame = new ProtocolFrame(
                java.util.List.of(new Opcode(Categories.TREE, Commands.Tree.CREATE_NODE, 0, 1, 0x10, 0)),
                new byte[0]);
        byte[] delta = FrameCodec.encodeFrame(frame, 7);
        assertEquals(7, FrameCodec.sequence(delta), "a delta batch carries its sequence");
        // The 1-arg overload is for non-delta batches (heartbeat/requests): sequence 0.
        assertEquals(0, FrameCodec.sequence(FrameCodec.encodeFrame(frame)));
    }

    @Test
    void isPingDetectsAMetaPingBatch() {
        // A META::PING heartbeat probe (guest → host), the exact vector-16a bytes.
        byte[] ping = new byte[16 + 16 + 4];
        ByteBuffer view = ByteBuffer.wrap(ping).order(ByteOrder.LITTLE_ENDIAN);
        view.putInt(0, FrameCodec.MAGIC);
        view.putShort(4, (short) FrameCodec.VERSION);
        view.putInt(12, 1); // opcodeCount
        view.put(16, (byte) Categories.META);
        view.put(17, (byte) Commands.Meta.PING);
        assertTrue(FrameCodec.isPing(ping), "a META::PING batch is detected");
        assertFalse(FrameCodec.isResync(ping), "a PING batch is not a RESYNC");
        assertFalse(FrameCodec.isPing(FrameCodec.encodeResync()), "a RESYNC batch is not a PING");
    }
}