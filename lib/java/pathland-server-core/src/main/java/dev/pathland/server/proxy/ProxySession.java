package dev.pathland.server.proxy;

import dev.pathland.server.PathlandTelemetry;
import dev.pathland.view.Categories;
import dev.pathland.view.Commands;
import dev.pathland.view.ProxyConfig;
import dev.pathland.view.emit.Opcode;
import dev.pathland.view.emit.ProtocolFrame;
import dev.pathland.view.transport.EnvironmentData;
import dev.pathland.view.transport.Event;
import dev.pathland.view.transport.FrameCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * One proxy's outbound origin connection (spec/PROXY.md): the proxying server
 * acting as a Pathland <b>host</b> to another server.
 *
 * <p>On connect it sends {@code META::ENVIRONMENT} (viewport + route) then
 * {@code META::RESYNC}. The origin replies with a full snapshot; this session
 * <b>remaps</b> the origin's node ids into its disjoint block
 * ({@link ProxyIdMap}), attaches the origin root under the proxy node, and hands
 * the frame to the client-facing sink (the session's {@code DeltaBatcher}). Live
 * deltas are remapped and forwarded the same way. Client events whose target is
 * in this proxy's range are remapped back and forwarded to the origin.
 *
 * <p><b>Threading.</b> Origin callbacks arrive on the transport's threads;
 * {@link #forwardEvent} arrives on the registry actor thread. The frame sink
 * ({@code DeltaBatcher.append}) is synchronized, so both paths are safe. This
 * session's own state is guarded by {@code lock}.
 *
 * <p><b>Backpressure</b> (spec/PROXY.md §8): the origin→client direction uses a
 * "chatty origin" breaker (a per-second batch cap → drop + {@code RESYNC}); the
 * client→origin direction coalesces continuous events (pointer-move) and never
 * drops discrete ones.
 */
public final class ProxySession {

    private static final Logger LOG = LoggerFactory.getLogger(ProxySession.class);

    private static final long RECONNECT_BASE_MILLIS = 500;
    private static final long RECONNECT_MAX_MILLIS = 30_000;
    private static final int MAX_RECONNECT_SHIFT = 6;

    /** The chatty-origin breaker: origin batches accepted per one-second window. */
    private static final int MAX_ORIGIN_BATCHES_PER_SECOND = 2_000;

    /** Coalesced pointer-move flush cadence. */
    private static final long MOVE_FLUSH_MILLIS = 16;

    private final int proxyId;
    private final int block;
    private final ProxyConfig config;
    private final String wid;
    private final float viewportWidth;
    private final float viewportHeight;
    private final ProxyTransport.Factory transportFactory;
    private final ScheduledExecutorService scheduler;
    private final Consumer<ProtocolFrame> frameSink;
    private final PathlandTelemetry telemetry;
    private final String mount;
    private final String instanceId;

    private final Object lock = new Object();

    private ProxyTransport transport;
    private boolean closed;
    private int attempts;
    private boolean snapshotReceived;
    private int originRootId = -1;
    private ProtocolFrame pendingSnapshot;

    private long windowStart = System.nanoTime();
    private int batchesInWindow;
    private boolean resyncRequestedThisWindow;

    /** Client → origin egress, in arrival order; pointer-moves coalesce by target. */
    private final List<Event> pendingEvents = new ArrayList<>();
    private boolean flushScheduled;

    public ProxySession(
            int proxyId,
            int block,
            ProxyConfig config,
            String wid,
            float viewportWidth,
            float viewportHeight,
            ProxyTransport.Factory transportFactory,
            ScheduledExecutorService scheduler,
            Consumer<ProtocolFrame> frameSink,
            PathlandTelemetry telemetry,
            String mount,
            String instanceId) {
        this.proxyId = proxyId;
        this.block = block;
        this.config = config;
        this.wid = wid == null ? "" : wid;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.transportFactory = transportFactory;
        this.scheduler = scheduler;
        this.frameSink = frameSink;
        this.telemetry = telemetry == null ? PathlandTelemetry.NOOP : telemetry;
        this.mount = mount == null ? "/" : mount;
        this.instanceId = instanceId;
    }

    public int proxyId() {
        return proxyId;
    }

    public int block() {
        return block;
    }

    public ProxyConfig config() {
        return config;
    }

    /** Whether {@code id} belongs to this proxy's block. */
    public boolean ownsId(int id) {
        return ProxyIdMap.isProxyId(id) && ProxyIdMap.blockOf(id) == block;
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /** Connect to the origin (idempotent while open; re-invoked on reconnect). */
    public void start() {
        synchronized (lock) {
            if (closed) {
                return;
            }
        }
        ProxyTransport t = transportFactory.create();
        synchronized (lock) {
            if (closed) {
                return;
            }
            transport = t;
        }
        try {
            t.connect(originWsUri(), handshakeHeaders(), new Listener());
        } catch (RuntimeException e) {
            handleDisconnect("connect failed: " + e.getMessage());
        }
    }

    /** Close the origin connection and stop any pending work (idempotent). */
    public void close() {
        ProxyTransport t;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            t = transport;
            transport = null;
            lock.notifyAll();
        }
        if (t != null) {
            t.close();
        }
    }

    /**
     * Block until the origin's first snapshot has been received and remapped (the
     * SSR path, spec/PROXY.md §3.1), or the timeout elapses.
     *
     * @return the remapped snapshot frame, or {@code null} on timeout/close
     */
    public ProtocolFrame requestSnapshot(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        synchronized (lock) {
            while (pendingSnapshot == null && !closed) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                lock.wait(Math.max(1, remaining / 1_000_000L));
            }
            return pendingSnapshot;
        }
    }

    // ── Client → origin (event egress) ──────────────────────────────────────

    /**
     * Forward a raw input event (already known to target this proxy's range) to
     * the origin. Continuous pointer-moves coalesce; discrete events flush at once.
     */
    public void forwardEvent(Event event) {
        boolean flushNow;
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (event.isPointerMove()) {
                replacePendingMove(event);
                if (!flushScheduled) {
                    flushScheduled = true;
                    scheduler.schedule(this::flushPendingEvents, MOVE_FLUSH_MILLIS, TimeUnit.MILLISECONDS);
                }
                flushNow = false;
            } else {
                pendingEvents.add(event);
                flushNow = true;
            }
        }
        if (flushNow) {
            flushPendingEvents();
        }
    }

    /** Flush buffered events to the origin (moves coalesced, discrete preserved). */
    public void flushPendingEvents() {
        List<Event> batch;
        synchronized (lock) {
            flushScheduled = false;
            if (pendingEvents.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(pendingEvents);
            pendingEvents.clear();
        }
        List<Event> origin = new ArrayList<>(batch.size());
        for (Event e : batch) {
            origin.add(new Event(e.command(), ProxyIdMap.toOriginId(e.target()), e.flags(),
                    e.x(), e.y(), e.value(), e.text(), e.b(), e.c()));
        }
        sendRaw(FrameCodec.encodeEvents(origin));
    }

    private void replacePendingMove(Event move) {
        for (int i = 0; i < pendingEvents.size(); i++) {
            Event e = pendingEvents.get(i);
            if (e.isPointerMove() && e.target() == move.target()) {
                pendingEvents.set(i, move);
                return;
            }
        }
        pendingEvents.add(move);
    }

    // ── Origin → client ─────────────────────────────────────────────────────

    private void onOriginBatch(byte[] bytes) {
        ProtocolFrame frame;
        try {
            frame = FrameCodec.decodeFrame(bytes);
        } catch (RuntimeException e) {
            LOG.warn("dropping malformed origin batch: {}", e.getMessage());
            return;
        }
        List<Opcode> ops = frame.opcodes();

        // Heartbeat (guest → host): reply PONG; never forward.
        if (hasMeta(ops, Commands.Meta.PING)) {
            sendRaw(FrameCodec.encodePong());
        }
        // META::RESET → scoped teardown of this proxy's subtree (never a global reset).
        if (hasMeta(ops, Commands.Meta.RESET)) {
            handleOriginReset();
        }

        // Forward only data opcodes (TREE/PARAMETER); META is proxy-internal.
        List<Opcode> data = new ArrayList<>(ops.size());
        for (Opcode op : ops) {
            if (op.category() != Categories.META) {
                data.add(op);
            }
        }
        if (data.isEmpty()) {
            return;
        }
        if (!allowBatch()) {
            telemetry.proxyBatchDropped(mount, "chatty origin");
            requestResync();
            return;
        }

        boolean snapshot = !snapshotReceived || createsRoot(data);
        List<Opcode> remapped = new ArrayList<>(data.size() + 1);
        if (snapshot) {
            int root = originRootId >= 0 ? originRootId : computeRoot(data);
            if (root >= 0) {
                originRootId = root;
                // Attach the origin's (parentless) root under the proxy node.
                remapped.add(new Opcode(Categories.TREE, Commands.Tree.INSERT_CHILD, 0,
                        proxyId, ProxyIdMap.toProxyId(block, root), -1));
            }
        }
        for (Opcode op : data) {
            remapped.add(ProxyIdMap.toProxy(op, block));
        }

        ProtocolFrame out = new ProtocolFrame(remapped, frame.strings());
        synchronized (lock) {
            snapshotReceived = true;
            pendingSnapshot = out;
            lock.notifyAll();
        }
        frameSink.accept(out);
    }

    private void handleOriginReset() {
        int root;
        synchronized (lock) {
            root = originRootId;
            originRootId = -1;
            snapshotReceived = false;
        }
        if (root >= 0) {
            frameSink.accept(new ProtocolFrame(
                    List.of(new Opcode(Categories.TREE, Commands.Tree.DELETE_NODE, 0,
                            ProxyIdMap.toProxyId(block, root), 0, 0)),
                    new byte[0]));
        }
    }

    private boolean createsRoot(List<Opcode> ops) {
        int root = originRootId;
        if (root < 0) {
            return true;
        }
        for (Opcode op : ops) {
            if (op.category() == Categories.TREE && op.command() == Commands.Tree.CREATE_NODE
                    && op.a() == root) {
                return true;
            }
        }
        return false;
    }

    /**
     * The origin's root id: the created node that is never a child in the batch
     * (the single parentless node, spec/OPCODE.md). Falls back to {@code 1} (the
     * reference emitters' root convention) when the batch is ambiguous.
     */
    private static int computeRoot(List<Opcode> ops) {
        Set<Integer> created = new HashSet<>();
        Set<Integer> children = new HashSet<>();
        for (Opcode op : ops) {
            if (op.category() != Categories.TREE) {
                continue;
            }
            if (op.command() == Commands.Tree.CREATE_NODE) {
                created.add(op.a());
            } else if (op.command() == Commands.Tree.INSERT_CHILD) {
                children.add(op.b());
            }
        }
        created.removeAll(children);
        if (created.size() == 1) {
            return created.iterator().next();
        }
        if (created.contains(1)) {
            return 1;
        }
        return created.stream().min(Integer::compareTo).orElse(1);
    }

    private boolean allowBatch() {
        long now = System.nanoTime();
        synchronized (lock) {
            if (now - windowStart > 1_000_000_000L) {
                windowStart = now;
                batchesInWindow = 0;
                resyncRequestedThisWindow = false;
            }
            batchesInWindow++;
            return batchesInWindow <= MAX_ORIGIN_BATCHES_PER_SECOND;
        }
    }

    private void requestResync() {
        synchronized (lock) {
            if (resyncRequestedThisWindow) {
                return;
            }
            resyncRequestedThisWindow = true;
        }
        telemetry.proxyResyncRequested(mount);
        sendRaw(FrameCodec.encodeResync());
    }

    // ── Connection plumbing ─────────────────────────────────────────────────

    private void handleDisconnect(String reason) {
        int root;
        synchronized (lock) {
            if (closed) {
                return;
            }
            root = originRootId;
            originRootId = -1;
            snapshotReceived = false;
        }
        telemetry.proxyDisconnected(mount, config.endpoint(), reason);
        // The origin session is per-connection: a reconnect gets fresh ids, so tear
        // down the old remapped subtree first (spec/PROXY.md §2.4).
        if (root >= 0) {
            frameSink.accept(new ProtocolFrame(
                    List.of(new Opcode(Categories.TREE, Commands.Tree.DELETE_NODE, 0,
                            ProxyIdMap.toProxyId(block, root), 0, 0)),
                    new byte[0]));
        }
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        long delay;
        synchronized (lock) {
            if (closed) {
                return;
            }
            attempts++;
            delay = Math.min(RECONNECT_MAX_MILLIS,
                    RECONNECT_BASE_MILLIS << Math.min(attempts - 1, MAX_RECONNECT_SHIFT));
        }
        scheduler.schedule(() -> {
            synchronized (lock) {
                if (closed) {
                    return;
                }
            }
            start();
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void sendRaw(byte[] bytes) {
        ProxyTransport t = transport;
        if (t != null && t.isOpen()) {
            t.send(bytes);
        }
    }

    private URI originWsUri() {
        String endpoint = config.endpoint();
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        String ws = base.startsWith("http") ? "ws" + base.substring(4) : base;
        return URI.create(ws + "/_pathland/ws?wid="
                + URLEncoder.encode(wid, StandardCharsets.UTF_8));
    }

    private Map<String, String> handshakeHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        if (instanceId != null && !instanceId.isBlank()) {
            headers.put("X-Pathland-Instance-Id", instanceId);
        }
        if (config.credential() != null && !config.credential().isBlank()) {
            headers.put("X-Pathland-Credential", config.credential());
        }
        return headers;
    }

    private static boolean hasMeta(List<Opcode> ops, int command) {
        for (Opcode op : ops) {
            if (op.category() == Categories.META && op.command() == command) {
                return true;
            }
        }
        return false;
    }

    /** The transport callbacks, wired to this session. */
    private final class Listener implements ProxyTransport.Listener {

        @Override
        public void onOpen() {
            synchronized (lock) {
                if (closed) {
                    return;
                }
                attempts = 0;
            }
            telemetry.proxyConnected(mount, config.endpoint());
            sendRaw(FrameCodec.encodeEnvironment(
                    new EnvironmentData(config.route(), viewportWidth, viewportHeight)));
            sendRaw(FrameCodec.encodeResync());
        }

        @Override
        public void onBinary(byte[] bytes) {
            try {
                onOriginBatch(bytes);
            } catch (RuntimeException e) {
                LOG.warn("proxy origin batch failed: {}", e.getMessage());
            }
        }

        @Override
        public void onClose(int code, String reason) {
            handleDisconnect("origin closed (" + code + ")");
        }

        @Override
        public void onError(Throwable error) {
            handleDisconnect(error == null ? "origin error" : String.valueOf(error.getMessage()));
        }
    }
}
