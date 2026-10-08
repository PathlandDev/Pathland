package com.pathland.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pathland.view.Button;
import com.pathland.view.state.InMemoryStateStore;
import com.pathland.view.transport.EnvironmentData;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The SSR HTML must reference the content-hashed bundle name so an immutable
 * asset cache is busted by a fresh URL per build; a classpath manifest
 * ({@code dom-renderer.current}) carries that name from the TypeScript copy
 * step to the server. Without a valid manifest the pre-hash literal is emitted.
 */
class PathlandAssetsTest {

    private static final String FIXTURE = "dom-renderer-3f9ac1d0e2ab.js";

    private static final Map<String, String> DEMO_MANIFEST = Map.of(
            "META-INF/resources/_pathland/" + PathlandAssets.MANIFEST, FIXTURE);

    @AfterEach
    void reset() {
        PathlandAssets.resetForTests();
    }

    @Test
    void readsTheHashedNameFromTheManifest() {
        assertEquals(FIXTURE, PathlandAssets.resolve(fixtureLookup(DEMO_MANIFEST)),
                "a Quarkus-style classpath manifest yields the hashed name");
    }

    @Test
    void resolvesSpringLayoutToo() {
        Map<String, String> spring = Map.of(
                "static/_pathland/" + PathlandAssets.MANIFEST, FIXTURE);
        assertEquals(FIXTURE, PathlandAssets.resolve(fixtureLookup(spring)));
    }

    @Test
    void fallsBackToTheLiteralWhenNoManifestIsPresent() {
        assertEquals(PathlandAssets.FALLBACK,
                PathlandAssets.resolve(fixtureLookup(Map.of())),
                "library-only consumers emit the pre-hash literal");
    }

    @Test
    void rejectsPathLikeOrMalformedNames() {
        Map<String, String> malicious = Map.of(
                "_pathland/" + PathlandAssets.MANIFEST, "../../evil.js");
        assertEquals(PathlandAssets.FALLBACK, PathlandAssets.resolve(fixtureLookup(malicious)),
                "a path-like name must not be emitted");
        assertEquals(PathlandAssets.FALLBACK,
                PathlandAssets.resolve(fixtureLookup(Map.of(
                        "_pathland/" + PathlandAssets.MANIFEST, "not-a-js-file"))),
                "a name without the .js suffix is rejected");
        assertEquals(PathlandAssets.FALLBACK,
                PathlandAssets.resolve(fixtureLookup(Map.of(
                        "_pathland/" + PathlandAssets.MANIFEST, "  "))),
                "a blank manifest line is rejected");
    }

    @Test
    void ssrHtmlEmitsTheHashedName() {
        String html = newPathlandSessionHtml();
        // The native renderer is not embedded on every test classpath; when it is
        // absent the SSR layer emits no script tag at all. The resolution/fallback
        // contract is covered by the unit tests above; the live SSR is verified
        // against the demo jars.
        org.junit.jupiter.api.Assumptions.assumeFalse(html.contains("Pathland renderer unavailable"),
                "native renderer embedded");
        assertTrue(html.contains("/_pathland/" + FIXTURE),
                "the SSR HTML references the manifest's content-hashed bundle");
        assertFalse(html.contains("dom-renderer.js\""),
                "the pre-hash literal is not referenced while a manifest is present");
    }

    private static PathlandAssets.Lookup fixtureLookup(Map<String, String> manifests) {
        return path -> {
            String value = manifests.get(path);
            if (value == null) {
                return null;
            }
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        };
    }

    private static String newPathlandSessionHtml() {
        PathlandApp app = () -> Button.of("t", () -> {});
        PathlandSession session = new PathlandSession(
                "s", new InMemoryStateStore(), app,
                EnvironmentData.of("/"));
        try {
            return session.renderHtml();
        } finally {
            session.close();
        }
    }
}