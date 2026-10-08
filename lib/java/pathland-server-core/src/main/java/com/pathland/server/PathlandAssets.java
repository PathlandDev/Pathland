package com.pathland.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Resolves the DOM-client bundle name that the SSR HTML must reference.
 *
 * <p>The bundle is copied into each demo's static resource dir under the
 * reserved framework prefix as a <strong>content-hashed</strong> filename
 * ({@code dom-renderer-&lt;sha256-12&gt;.js} — see the TypeScript
 * {@code copy-to-demos} script) because the asset is served with immutable
 * cache headers; a fresh name per build is what busts the cache. A one-line
 * pointer manifest {@code dom-renderer.current} sits next to the copies so the
 * server can emit the current name without re-hashing:
 *
 * <pre>
 *   dom-renderer-3f9ac1d0e2ab.js
 * </pre>
 *
 * <p>The manifest is looked up on the classpath — the Quarkus demo keeps its
 * copy under {@code META-INF/resources/_pathland/}, the Spring Boot demo under
 * {@code static/_pathland/} (both resolve to {@code _pathland/} on the
 * classpath). Resolved once per JVM and cached; when no manifest is present
 * (a library-only consumer without an embedded bundle, or a unit test) the
 * pre-hash literal {@code dom-renderer.js} is returned so behaviour is
 * unchanged.
 */
final class PathlandAssets {

    static final String MANIFEST = "dom-renderer.current";
    static final String FALLBACK = "dom-renderer.js";

    private static final String[] MANIFEST_PATHS = {
        "META-INF/resources/_pathland/" + MANIFEST,
        "static/_pathland/" + MANIFEST,
        "_pathland/" + MANIFEST,
    };

    /** Opens a classpath resource, or {@code null} when absent/read error. */
    @FunctionalInterface
    interface Lookup {
        InputStream open(String path);
    }

    private static volatile String bundleName;

    private PathlandAssets() {}

    /** The current bundle filename (hashed on the demo classpath, literal otherwise). */
    static String bundleName() {
        String name = bundleName;
        if (name != null) {
            return name;
        }
        synchronized (PathlandAssets.class) {
            if (bundleName == null) {
                bundleName = resolve(PathlandAssets::openClasspath);
            }
            return bundleName;
        }
    }

    /** Forget the cached name (tests). */
    static void resetForTests() {
        synchronized (PathlandAssets.class) {
            bundleName = null;
        }
    }

    /** Deterministic resolution against an explicit resource lookup (tests). */
    static String resolve(Lookup lookup) {
        for (String path : MANIFEST_PATHS) {
            try (InputStream in = lookup.open(path)) {
                if (in == null) {
                    continue;
                }
                String name = readFirstLine(in);
                if (validBundleName(name)) {
                    return name;
                }
            } catch (IOException ignored) {
                // fall through to the fallback
            }
        }
        return FALLBACK;
    }

    private static InputStream openClasspath(String path) {
        return PathlandAssets.class.getClassLoader().getResourceAsStream(path);
    }

    private static String readFirstLine(InputStream in) throws IOException {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        }
    }

    /** A bare basename: letters, digits, dot, underscore, hyphen — never a path. */
    private static boolean validBundleName(String name) {
        return name != null
                && !name.isEmpty()
                && name.endsWith(".js")
                && name.chars().allMatch(c ->
                        Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-');
    }
}