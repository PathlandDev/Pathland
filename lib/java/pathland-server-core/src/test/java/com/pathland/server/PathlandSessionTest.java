package com.pathland.server;

import com.pathland.view.Button;
import com.pathland.view.state.InMemoryStateStore;
import com.pathland.view.state.StateStore;
import com.pathland.view.transport.EnvironmentData;
import com.pathland.view.transport.Event;
import com.pathland.view.transport.FrameCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transport-agnostic session runtime: mounts the app's root (with the active-path
 * signal provided), routes inbound events into the app's bindings, applies the platform
 * environment, and renders SSR HTML — all independent of the web framework.
 */
class PathlandSessionTest {

    private static final StateStore STORE = new InMemoryStateStore();

    @Test
    void mountsAndDispatchesTaps() {
        AtomicInteger taps = new AtomicInteger();
        PathlandApp app = () -> Button.of("Tap", taps::incrementAndGet);
        PathlandSession session = new PathlandSession("s1", STORE, app, EnvironmentData.of("/"));

        // The root Button is node id 1; a POINTER_UP routed to it runs its action.
        session.dispatch(FrameCodec.encodeEvents(List.of(Event.pointerUp(1, 0, 0))));
        assertEquals(1, taps.get(), "a routed tap runs the app action");

        String html = session.renderHtml();
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>") || html.contains("Pathland renderer unavailable"),
                "SSR renders the tree (or reports the renderer is unavailable)");

        session.close();
    }

    @Test
    void applyEnvironmentAndResyncDoNotThrow() {
        PathlandApp app = () -> Button.of("Tap", () -> {});
        PathlandSession session = new PathlandSession("s1", STORE, app, EnvironmentData.of("/"));

        session.applyEnvironment(EnvironmentData.of("/kitchen"));
        session.resync();
        session.close(); // idempotent
        session.close();
    }

    @Test
    void registryRendersHtmlAndTearsDown() {
        PathlandRegistry registry = new PathlandRegistry(() -> Button.of("Tap", () -> {}), STORE);
        String html = registry.renderHtml("s1", "/");
        assertNotNull(html);

        registry.open("s1", new NoopConnection());
        registry.environment("s1", EnvironmentData.of("/kitchen"));
        registry.close("s1");
        registry.shutdown();
    }

    @Test
    void debugHtmlSessionEmitsNodeCommentsWhenRendererAvailable() {
        PathlandSession session = new PathlandSession(
                "s1", STORE, () -> Button.of("Tap", () -> {}), EnvironmentData.of("/"), true);
        String html = session.renderHtml();
        if (html.contains("Pathland renderer unavailable")) {
            return; // dylib not on java.library.path in this test JVM — nothing to assert
        }
        assertTrue(html.contains("<!-- #1 Button"), "debug SSR comments on every node: " + html);
        session.close();

        // Default (property off) leaves the SSR output comment-free.
        PathlandSession plain = new PathlandSession(
                "s1", STORE, () -> Button.of("Tap", () -> {}), EnvironmentData.of("/"));
        String plainHtml = plain.renderHtml();
        if (!plainHtml.contains("Pathland renderer unavailable")) {
            assertTrue(!plainHtml.contains("<!--"), "debug comments are opt-in: " + plainHtml);
        }
        plain.close();
    }

    @Test
    void registryPropagatesDebugHtmlFlag() {
        PathlandRegistry debug = new PathlandRegistry(() -> Button.of("Tap", () -> {}), STORE, true);
        String html = debug.renderHtml("s1", "/");
        if (!html.contains("Pathland renderer unavailable")) {
            assertTrue(html.contains("<!-- #1 Button"), "registry flag reaches SSR: " + html);
        }
        debug.shutdown();
    }

    /** A connection that swallows sends (the socket adapters exercise real sends). */
    private static final class NoopConnection implements PathlandConnection {
        @Override
        public void send(byte[] bytes) {
            // no-op
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }
}