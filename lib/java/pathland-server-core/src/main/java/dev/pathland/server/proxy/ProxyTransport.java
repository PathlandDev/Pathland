package dev.pathland.server.proxy;

import java.net.URI;
import java.util.Map;

/**
 * The outbound connection seam a proxying server uses to reach an origin server
 * (spec/PROXY.md §1, §7). A proxying server acts as a Pathland <b>host</b>
 * (client) to the origin: it sends {@code META::ENVIRONMENT} + {@code META::RESYNC},
 * receives the origin's {@code TREE}/{@code PARAMETER} batches, and sends
 * {@code EVENT} batches back.
 *
 * <p>Abstracted so a host can swap the transport (the default is the JDK
 * {@link java.net.http.WebSocket} client, {@link JdkWebSocketTransport}); the
 * framework starters may supply a native client (Quarkus websockets-client,
 * Spring {@code StandardWebSocketClient}).
 */
public interface ProxyTransport {

    /**
     * Connect to the origin. The listener's callbacks are invoked on the
     * transport's own threads; a {@code ProxySession} serializes their work.
     *
     * @param uri     the origin WebSocket URI ({@code ws(s)://host/<base>/ws?wid=…})
     * @param headers extra handshake headers (service credentials, the proxying
     *                server's instance id for the cycle guard)
     */
    void connect(URI uri, Map<String, String> headers, Listener listener);

    /** Send one binary batch to the origin. No-op when not open. */
    void send(byte[] bytes);

    /** Whether the connection is currently open. */
    boolean isOpen();

    /** Close the connection (idempotent). */
    void close();

    /** Lifecycle callbacks from the origin connection. */
    interface Listener {
        /** The handshake completed — the session may now send ENVIRONMENT + RESYNC. */
        void onOpen();

        /** One complete binary message from the origin. */
        void onBinary(byte[] bytes);

        /** The origin closed the connection. */
        void onClose(int code, String reason);

        /** A transport error (the session treats it as a close and retries). */
        void onError(Throwable error);
    }

    /** Creates one transport per origin connection. */
    interface Factory {
        ProxyTransport create();
    }
}
