package com.pathland.server.test;

import com.pathland.view.emit.Frame;
import com.pathland.view.emit.Opcode;
import com.pathland.view.transport.EnvironmentData;
import com.pathland.view.transport.Event;
import com.pathland.view.transport.FrameCodec;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * A minimal Pathland WebSocket test client, shared by the starters' multi-session
 * stress tests. Wraps a JDK {@link WebSocket}, speaks the PLPL wire protocol
 * (environment / resync / click / ping), and collects every decoded {@link Frame}.
 * Each connection is a separate session (a fresh {@code wid} scopes its persisted
 * state; the server keys the UI model by the per-connection id).
 *
 * <p>Batches are single-frame WebSocket messages (both sides send each PLPL batch
 * as one frame), so each {@code onBinary} is decoded as one batch.
 */
public final class WsSession implements AutoCloseable {

    /** A JDK WebSocket listener collecting decoded frames + close/error state.
     *  Frames arrive on the listener thread while the test thread polls/iterates, so
     *  both collections are {@link CopyOnWriteArrayList} (thread-safe for add+iterate). */
    private static final class Listener implements WebSocket.Listener {
        final List<Frame> frames = new CopyOnWriteArrayList<>();
        final List<Integer> peerCloseCodes = new CopyOnWriteArrayList<>();
        volatile boolean open;

        @Override
        public void onOpen(WebSocket webSocket) {
            open = true;
            webSocket.request(1); // overriding onOpen disables the default request(1)
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            try {
                frames.add(FrameCodec.decodeFrame(bytes));
            } catch (RuntimeException ignored) {
                // a partial/undecodable batch — keep scanning (defensive)
            }
            webSocket.request(1); // keep delivering messages
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            peerCloseCodes.add(statusCode);
            open = false;
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            open = false;
        }
    }

    private final WebSocket socket;
    private final Listener listener;
    private volatile boolean closedByUs;

    private WsSession(WebSocket socket, Listener listener) {
        this.socket = socket;
        this.listener = listener;
    }

    /** Connect a Pathland session to {@code wsUrl}/_pathland/ws?wid=… (blocking up to 10 s). */
    public static WsSession connect(String wsBaseUrl, String wid) throws Exception {
        Listener listener = new Listener();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(wsBaseUrl + "?wid=" + wid), listener)
                .get(10, TimeUnit.SECONDS);
        return new WsSession(socket, listener);
    }

    /** Send the platform environment (viewport + route) — the DOM client's first message. */
    public WsSession sendEnvironment(float width, float height, String route) {
        return send(FrameCodec.encodeEnvironment(new EnvironmentData(route, width, height)));
    }

    /** Request a full snapshot of the current tree. */
    public WsSession sendResync() {
        return send(FrameCodec.encodeResync());
    }

    /** A tap (POINTER_UP) on a node — e.g. a button's click. */
    public WsSession sendClick(int nodeId) {
        return send(FrameCodec.encodeEvents(List.of(Event.pointerUp(nodeId, 0f, 0f))));
    }

    /** A META::PING heartbeat probe. */
    public WsSession sendPing() {
        return send(FrameCodec.encodePing());
    }

    private WsSession send(byte[] batch) {
        socket.sendBinary(ByteBuffer.wrap(batch), true).join();
        return this;
    }

    /** Every decoded frame so far (all batches received on this connection). */
    public List<Frame> frames() {
        return Collections.unmodifiableList(listener.frames);
    }

    /** True when the peer closed the connection before this client did. */
    public boolean wasClosedByPeer() {
        return !listener.peerCloseCodes.isEmpty() && !closedByUs;
    }

    public boolean isOpen() {
        return listener.open;
    }

    /** Whether any received batch carries a META::PONG (a heartbeat reply). */
    public boolean sawPong() {
        return listener.frames.stream().flatMap(f -> f.opcodes().stream())
                .anyMatch(op -> op.category() == 0x04 && op.command() == 0x05);
    }

    /** All SET_TEXT string values received (the label sequence of a counter app). */
    public List<String> textValues() {
        List<String> values = new ArrayList<>();
        for (Frame frame : listener.frames) {
            for (Opcode op : frame.opcodes()) {
                if (op.category() == 0x02 && op.command() == 0x03) { // PARAMETER::SET_TEXT
                    values.add(frame.stringAt(op.b()));
                }
            }
        }
        return values;
    }

    /** Total opcodes received. */
    public int opcodeCount() {
        return listener.frames.stream().mapToInt(f -> f.opcodes().size()).sum();
    }

    /** Poll until a frame matching {@code predicate} arrives or the timeout elapses. */
    public boolean awaitFrame(Predicate<Frame> predicate, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (listener.frames.stream().anyMatch(predicate)) {
                return true;
            }
            if (!listener.open) {
                return false; // the connection died: nothing more will arrive
            }
            Thread.sleep(5);
        }
        return listener.frames.stream().anyMatch(predicate);
    }

    /** Gracefully close this session (the peer close that follows is expected, not a failure). */
    @Override
    public void close() {
        closedByUs = true;
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "test done").get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            socket.abort();
        }
    }
}