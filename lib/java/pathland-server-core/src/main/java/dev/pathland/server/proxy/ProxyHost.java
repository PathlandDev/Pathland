package dev.pathland.server.proxy;

import dev.pathland.server.PathlandTelemetry;
import dev.pathland.view.ProxyConfig;
import dev.pathland.view.emit.ProtocolFrame;
import dev.pathland.view.transport.Event;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Owns the {@link ProxySession}s of one application session (spec/PROXY.md): one
 * origin connection per {@code PROXY_VIEW} node, each with its own disjoint
 * node-id block. Routes inbound client events whose target is in a proxy's range
 * to the owning origin connection.
 *
 * <p>Created by {@code PathlandSession} when the live connection attaches (the
 * SSR path renders a placeholder in v1; the origin subtree hydrates over the
 * live connection).
 */
public final class ProxyHost {

    private final Map<Integer, ProxySession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger nextBlock = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler;
    private final ProxyTransport.Factory transportFactory;
    private final Consumer<ProtocolFrame> frameSink;
    private final PathlandTelemetry telemetry;
    private final String mount;
    private final String wid;
    private final float viewportWidth;
    private final float viewportHeight;
    private final String instanceId;

    public ProxyHost(
            Consumer<ProtocolFrame> frameSink,
            String wid,
            float viewportWidth,
            float viewportHeight,
            PathlandTelemetry telemetry,
            String mount) {
        this(frameSink, wid, viewportWidth, viewportHeight, telemetry, mount,
                JdkWebSocketTransport.factory());
    }

    public ProxyHost(
            Consumer<ProtocolFrame> frameSink,
            String wid,
            float viewportWidth,
            float viewportHeight,
            PathlandTelemetry telemetry,
            String mount,
            ProxyTransport.Factory transportFactory) {
        this.frameSink = frameSink;
        this.wid = wid;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.telemetry = telemetry == null ? PathlandTelemetry.NOOP : telemetry;
        this.mount = mount == null ? "/" : mount;
        this.transportFactory = transportFactory;
        this.instanceId = UUID.randomUUID().toString();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pathland-proxy");
            t.setDaemon(true);
            return t;
        });
    }

    /** Register and start a proxy for a {@code PROXY_VIEW} node. */
    public ProxySession add(int proxyId, ProxyConfig config) {
        int block = nextBlock.getAndIncrement();
        if (block >= ProxyIdMap.MAX_BLOCKS) {
            throw new IllegalStateException("too many proxy views (max " + ProxyIdMap.MAX_BLOCKS + ")");
        }
        ProxySession session = new ProxySession(
                proxyId, block, config, wid, viewportWidth, viewportHeight,
                transportFactory, scheduler, frameSink, telemetry, mount, instanceId);
        sessions.put(proxyId, session);
        session.start();
        return session;
    }

    /**
     * Route an inbound client event to the owning origin connection.
     *
     * @return {@code true} when the event targeted a proxy range (and was
     *         forwarded, or dropped because the proxy is gone); {@code false}
     *         when it belongs to the app itself
     */
    public boolean forward(Event event) {
        if (!ProxyIdMap.isProxyId(event.target())) {
            return false;
        }
        int block = ProxyIdMap.blockOf(event.target());
        for (ProxySession session : sessions.values()) {
            if (session.block() == block) {
                session.forwardEvent(event);
                return true;
            }
        }
        return true; // a proxy-range id with no live proxy: drop, don't dispatch locally
    }

    /** Number of live origin connections (a health/metrics gauge). */
    public int activeProxies() {
        return sessions.size();
    }

    /** Close every origin connection and stop the proxy scheduler. */
    public void close() {
        for (ProxySession session : sessions.values()) {
            session.close();
        }
        sessions.clear();
        scheduler.shutdownNow();
    }
}
