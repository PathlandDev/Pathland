package dev.pathland.server.proxy;

import dev.pathland.server.PathlandTelemetry;
import dev.pathland.view.Categories;
import dev.pathland.view.Commands;
import dev.pathland.view.ProxyConfig;
import dev.pathland.view.emit.Opcode;
import dev.pathland.view.emit.ProtocolFrame;
import dev.pathland.view.transport.Event;
import dev.pathland.view.transport.FrameCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A proxy session against a fake origin transport (spec/PROXY.md): the
 * ENVIRONMENT+RESYNC handshake, id remapping, root attachment, event egress,
 * reset teardown, and the backpressure breaker.
 */
class ProxySessionTest {

    private static final int PROXY_ID = 3;
    private static final ProxyConfig CONFIG = new ProxyConfig("http://b.example", "/music", "svc");

    private final FakeTransport transport = new FakeTransport();
    private final List<ProtocolFrame> frames = Collections.synchronizedList(new ArrayList<>());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "proxy-test");
        t.setDaemon(true);
        return t;
    });
    private ProxySession session;

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
        scheduler.shutdownNow();
    }

    private ProxySession start() {
        session = new ProxySession(
                PROXY_ID, 0, CONFIG, "w1", 800f, 600f,
                () -> transport, scheduler, frames::add, PathlandTelemetry.NOOP, "/", "instance-1");
        session.start();
        return session;
    }

    private int base() {
        return ProxyIdMap.base(session.block());
    }

    @Test
    void connectsAndSendsEnvironmentThenResync() {
        start();
        assertEquals(2, transport.sent.size(), "ENVIRONMENT then RESYNC");
        assertTrue(FrameCodec.isEnvironment(transport.sent.get(0)), "first message is the environment");
        assertTrue(FrameCodec.isResync(transport.sent.get(1)), "second message requests the snapshot");
        assertEquals("ws://b.example/_pathland/ws?wid=w1", transport.uri.toString());
        assertEquals("svc", transport.headers.get("X-Pathland-Credential"));
        assertNotNull(transport.headers.get("X-Pathland-Instance-Id"), "cycle-guard instance id");
    }

    @Test
    void remapsOriginSnapshotAndAttachesTheRootUnderTheProxyNode() {
        start();
        // Origin snapshot: CREATE_NODE 1 VSTACK, CREATE_NODE 2 TEXT, INSERT_CHILD 1 2.
        transport.deliver(FrameCodec.encodeFrame(new ProtocolFrame(List.of(
                new Opcode(Categories.TREE, Commands.Tree.CREATE_NODE, 0, 1, 0x10, 0),
                new Opcode(Categories.TREE, Commands.Tree.CREATE_NODE, 0, 2, 0x01, 0),
                new Opcode(Categories.TREE, Commands.Tree.INSERT_CHILD, 0, 1, 2, -1)), new byte[0])));

        assertEquals(1, frames.size());
        List<Opcode> ops = frames.get(0).opcodes();
        int base = base();
        assertEquals(4, ops.size());
        // The origin's opcodes are remapped verbatim, in order.
        assertEquals(base + 1, ops.get(0).a());
        assertEquals(base + 2, ops.get(1).a());
        assertEquals(base + 1, ops.get(2).a());
        assertEquals(base + 2, ops.get(2).b());
        // The proxy attaches the origin's parentless root under the proxy node, appended
        // so the root's CREATE_NODE precedes the INSERT_CHILD.
        Opcode attach = ops.get(3);
        assertEquals(Commands.Tree.INSERT_CHILD, attach.command());
        assertEquals(PROXY_ID, attach.a(), "attached to the proxy node");
        assertEquals(base + 1, attach.b(), "the remapped origin root");
        assertEquals(-1, attach.c(), "APPEND");
    }

    @Test
    void remapsOriginDeltaAndKeepsItsStringSection() {
        start();
        transport.deliver(snapshot());
        // A delta: SET_TEXT on origin node 2, string "hi" at relative offset 0.
        transport.deliver(FrameCodec.encodeFrame(new ProtocolFrame(List.of(
                new Opcode(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 2, 0, 0)),
                stringSection("hi"))));

        assertEquals(2, frames.size());
        Opcode op = frames.get(1).opcodes().get(0);
        assertEquals(base() + 2, op.a(), "delta target remapped");
        assertEquals("hi", frames.get(1).stringAt(0), "string section passes through verbatim");
    }

    @Test
    void forwardsClientEventRemappedBackToTheOrigin() {
        start();
        int proxyTarget = base() + 2;
        session.forwardEvent(Event.pointerUp(proxyTarget, 10f, 20f));

        List<Event> events = FrameCodec.decodeEvents(transport.sent.get(transport.sent.size() - 1));
        assertEquals(1, events.size());
        assertEquals(2, events.get(0).target(), "target unmapped to the origin id");
        assertEquals(Commands.Event.POINTER_UP, events.get(0).command());
    }

    @Test
    void coalescesPointerMovesPerTarget() {
        start();
        int proxyTarget = base() + 2;
        session.forwardEvent(Event.pointerMove(proxyTarget, 1f, 1f, 0));
        session.forwardEvent(Event.pointerMove(proxyTarget, 2f, 2f, 0));
        session.flushPendingEvents();

        long moves = transport.sent.stream()
                .filter(b -> !FrameCodec.isEnvironment(b) && !FrameCodec.isResync(b))
                .flatMap(b -> FrameCodec.decodeEvents(b).stream())
                .filter(Event::isPointerMove)
                .count();
        assertEquals(1, moves, "two moves for one target coalesce to one");
    }

    @Test
    void originResetTearsDownTheRemappedSubtree() {
        start();
        transport.deliver(snapshot());
        transport.deliver(FrameCodec.encodeFrame(new ProtocolFrame(List.of(
                new Opcode(Categories.META, Commands.Meta.RESET, 0, 0, 0, 0)), new byte[0])));

        assertEquals(2, frames.size(), "the reset does not forward a global META::RESET");
        Opcode del = frames.get(1).opcodes().get(0);
        assertEquals(Commands.Tree.DELETE_NODE, del.command());
        assertEquals(base() + 1, del.a(), "the remapped origin root is deleted");
    }

    @Test
    void disconnectTearsDownTheSubtree() {
        start();
        transport.deliver(snapshot());
        transport.listener.onError(new RuntimeException("boom"));

        Opcode last = frames.get(frames.size() - 1).opcodes().get(0);
        assertEquals(Commands.Tree.DELETE_NODE, last.command());
        assertEquals(base() + 1, last.a());
    }

    @Test
    void dropsAndResyncsWhenTheOriginFloods() {
        start();
        transport.deliver(snapshot());
        int before = transport.sent.size();
        for (int i = 0; i < 2_001; i++) {
            transport.deliver(FrameCodec.encodeFrame(new ProtocolFrame(List.of(
                    new Opcode(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 2, 0, 0)),
                    stringSection("x"))));
        }
        assertTrue(transport.sent.size() > before, "the breaker asked the origin to resync");
        assertTrue(FrameCodec.isResync(transport.sent.get(transport.sent.size() - 1)));
    }

    @Test
    void dropsTheOriginsGlobalTokenOverrides() {
        start();
        // The origin's full frame starts with its theme (SET_DESIGN_TOKEN). A proxied
        // app renders under the HOST's theme, so the origin's global overrides are
        // dropped (spec/PROXY.md §9) — and never forwarded with a remapped (bad) offset.
        byte[] strings = concat(stringSection("color.primary"), stringSection("Hi"));
        int textOffset = stringSection("color.primary").length;
        transport.deliver(FrameCodec.encodeFrame(new ProtocolFrame(List.of(
                new Opcode(Categories.PARAMETER, Commands.Parameter.SET_DESIGN_TOKEN, 0, 0, 0x07, 0xFF00_00FF),
                new Opcode(Categories.TREE, Commands.Tree.CREATE_NODE, 0, 1, 0x10, 0),
                new Opcode(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 1, textOffset, 0)), strings)));

        ProtocolFrame forwarded = frames.get(frames.size() - 1);
        assertFalse(forwarded.opcodes().stream().anyMatch(o ->
                        o.category() == Categories.PARAMETER
                                && o.command() == Commands.Parameter.SET_DESIGN_TOKEN),
                "the origin's global token overrides are dropped");
        // Every string-bearing op references a valid offset in the forwarded section.
        for (Opcode op : forwarded.opcodes()) {
            if (op.category() == Categories.PARAMETER && op.command() == Commands.Parameter.SET_TEXT) {
                assertEquals("Hi", forwarded.stringAt(op.b()));
            }
        }
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static byte[] snapshot() {
        return FrameCodec.encodeFrame(new ProtocolFrame(List.of(
                new Opcode(Categories.TREE, Commands.Tree.CREATE_NODE, 0, 1, 0x10, 0),
                new Opcode(Categories.TREE, Commands.Tree.CREATE_NODE, 0, 2, 0x01, 0),
                new Opcode(Categories.TREE, Commands.Tree.INSERT_CHILD, 0, 1, 2, -1)), new byte[0]));
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

    /** A {@link ProxyTransport} that records what the session sends and lets a test deliver origin bytes. */
    static final class FakeTransport implements ProxyTransport {

        final List<byte[]> sent = Collections.synchronizedList(new ArrayList<>());
        volatile ProxyTransport.Listener listener;
        URI uri;
        Map<String, String> headers;
        boolean open = true;

        @Override
        public void connect(URI uri, Map<String, String> headers, Listener listener) {
            this.uri = uri;
            this.headers = headers;
            this.listener = listener;
            listener.onOpen();
        }

        @Override
        public void send(byte[] bytes) {
            sent.add(bytes);
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }

        void deliver(byte[] bytes) {
            listener.onBinary(bytes);
        }
    }
}
