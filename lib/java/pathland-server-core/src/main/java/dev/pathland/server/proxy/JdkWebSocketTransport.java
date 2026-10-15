package dev.pathland.server.proxy;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * The default {@link ProxyTransport}: the JDK {@link HttpClient} WebSocket client
 * (no extra dependency, mirrors {@code pathland-test-kit.WsSession}). One instance
 * per origin connection.
 */
public final class JdkWebSocketTransport implements ProxyTransport {

    private final HttpClient http;
    private volatile WebSocket socket;

    public JdkWebSocketTransport() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    public JdkWebSocketTransport(HttpClient http) {
        this.http = http;
    }

    /** A {@link ProxyTransport.Factory} producing JDK-backed transports. */
    public static ProxyTransport.Factory factory() {
        return JdkWebSocketTransport::new;
    }

    @Override
    public void connect(URI uri, Map<String, String> headers, Listener listener) {
        WebSocket.Builder builder = http.newWebSocketBuilder();
        headers.forEach(builder::header);
        builder.buildAsync(uri, new Handler(listener)).whenComplete((ws, error) -> {
            if (error != null) {
                listener.onError(error);
            } else {
                socket = ws;
                listener.onOpen();
            }
        });
    }

    @Override
    public void send(byte[] bytes) {
        WebSocket ws = socket;
        if (ws == null) {
            return;
        }
        ws.sendBinary(ByteBuffer.wrap(bytes), true).whenComplete((ignored, error) -> {
            if (error != null) {
                // The session owns error handling; a failed send surfaces as a close.
            }
        });
    }

    @Override
    public boolean isOpen() {
        WebSocket ws = socket;
        return ws != null && !ws.isOutputClosed() && !ws.isInputClosed();
    }

    @Override
    public void close() {
        WebSocket ws = socket;
        socket = null;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        }
    }

    /** Accumulates (possibly fragmented) binary frames into whole messages. */
    private static final class Handler implements WebSocket.Listener {

        private final Listener listener;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        Handler(Listener listener) {
            this.listener = listener;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            buffer.writeBytes(chunk);
            if (last) {
                byte[] message = buffer.toByteArray();
                buffer.reset();
                listener.onBinary(message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            listener.onClose(statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            listener.onError(error);
        }
    }
}
