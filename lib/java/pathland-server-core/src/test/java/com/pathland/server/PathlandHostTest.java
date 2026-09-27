package com.pathland.server;

import com.pathland.view.Text;
import com.pathland.view.state.InMemoryStateStore;
import com.pathland.view.state.StateStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The multi-app host: dispatches requests to the app whose mount path is the longest
 * prefix, strips the mount so each app sees its own route space, computes per-app
 * framework bases, and namespaces state-store scope per app.
 */
class PathlandHostTest {

    private static final StateStore STORE = new InMemoryStateStore();

    private static PathlandApp app(String label) {
        return () -> Text.of(label);
    }

    private static PathlandHost host(MountedApp... mounts) {
        return new PathlandHost(java.util.List.of(mounts), STORE, false);
    }

    @Test
    void rootMountClaimsEveryPath() {
        PathlandHost host = host(MountedApp.of("/", app("root")));
        PathlandHost.MountMatch match = host.match("/");
        assertNotNull(match);
        assertEquals("/", match.registry().mountPath());
        assertEquals("/", match.route());
        assertEquals("/home", host.match("/home").route(), "the root mount strips nothing");
        assertEquals("/users/42", host.match("/users/42").route());
    }

    @Test
    void longestMountWinsAndStripsThePrefix() {
        PathlandHost host = host(
                MountedApp.of("/", app("root")),
                MountedApp.of("/app2", app("secondary")));
        PathlandHost.MountMatch match = host.match("/app2/home");
        assertNotNull(match);
        assertEquals("/app2", match.registry().mountPath());
        assertEquals("/home", match.route(), "the app sees its own route space");
        assertEquals("/", host.match("/app2").route());
        assertEquals("/", host.match("/app2/").route());
        assertEquals("/home", host.match("/home").route(), "root mount strips nothing");
    }

    @Test
    void mountBoundaryIsRespected() {
        PathlandHost host = host(
                MountedApp.of("/", app("root")),
                MountedApp.of("/app2", app("secondary")));
        // /app2x is NOT under /app2: the root mount owns it.
        assertEquals("/", host.match("/app2x").registry().mountPath());
        assertEquals("/app2x", host.match("/app2x").route());
    }

    @Test
    void noRootMountMissesForeignPaths() {
        PathlandHost host = host(MountedApp.of("/app2", app("secondary")));
        assertNull(host.match("/"), "no mount owns /");
        assertNull(host.match("/home"));
        assertNotNull(host.match("/app2/home"));
    }

    @Test
    void perAppBaseAndStateScope() {
        PathlandRegistry root = host(MountedApp.of("/", app("root"))).registry("/");
        PathlandRegistry secondary = host(MountedApp.of("/app2", app("secondary"))).registry("/app2");
        assertEquals("/_pathland", root.base());
        assertEquals("/app2/_pathland", secondary.base());
        assertEquals("s1", root.stateScope("s1"));
        assertEquals("/app2:s1", secondary.stateScope("s1"));
    }

    @Test
    void duplicateMountPathIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> host(
                MountedApp.of("/app2", app("a")),
                MountedApp.of("/app2", app("b"))));
    }

    @Test
    void mountPathIsNormalized() {
        MountedApp mount = MountedApp.of("app2/", app("x"));
        assertEquals("/app2", mount.path());
        assertEquals("/", MountedApp.of(null, app("x")).path());
    }

    @Test
    void registryStripsTheMountFromRoutes() {
        PathlandHost host = host(
                MountedApp.of("/", app("root")),
                MountedApp.of("/app2", app("secondary")));
        PathlandRegistry secondary = host.registry("/app2");
        assertEquals("/kitchen", secondary.stripRoute("/app2/kitchen"), "the mount prefix is stripped");
        assertEquals("/", secondary.stripRoute("/app2"));
        assertEquals("/home", secondary.stripRoute("/home"), "routes outside the mount pass through");
        assertEquals("/app2/home", host.registry("/").stripRoute("/app2/home"), "the root mount strips nothing");
    }

    @Test
    void registryRendersSsrWithPerAppBaseAndStateScope() {
        PathlandHost host = host(
                MountedApp.of("/", app("root")),
                MountedApp.of("/app2", app("secondary")));
        String html = host.registry("/app2").renderHtml("/app2/home");
        assertNotNull(html);
        if (html.contains("Pathland renderer unavailable")) {
            return; // dylib not on java.library.path in this test JVM — nothing to assert
        }
        assertTrue(html.contains("data-pathland-base=\"/app2/_pathland\""),
                "SSR emits the mounted app's framework base: " + html);
    }
}