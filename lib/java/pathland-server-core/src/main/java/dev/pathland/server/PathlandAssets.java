package dev.pathland.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the DOM-client bundle name that the SSR HTML must reference.
 *
 * <p>The bundle ships in the framework libraries under the reserved framework
 * prefix as a <strong>content-hashed</strong> filename
 * ({@code dom-renderer-&lt;sha256-12&gt;.js} — see the TypeScript
 * {@code copy-to-libs} script) because the asset is served with immutable
 * cache headers; a fresh name per build is what busts the cache. A pointer
 * manifest {@code dom-renderer.current} sits next to the copies so the server
 * can emit the current name without re-hashing, plus the Subresource Integrity
 * digest for the {@code <script>} tag:
 *
 * <pre>
 *   dom-renderer-3f9ac1d0e2ab.js
 *   sha384-…
 * </pre>
 *
 * <p>The manifest is looked up on the classpath — the Quarkus extension ships
 * its copy under {@code META-INF/resources/_pathland/}, the Spring Boot starter
 * under {@code static/_pathland/} (both resolve to {@code _pathland/} on the
 * classpath); an application may override either location. Resolved once per
 * JVM and cached; when no manifest is present (a library-only consumer without
 * an embedded bundle, or a unit test) the pre-hash literal
 * {@code dom-renderer.js} is returned and no integrity is emitted, so behaviour
 * is unchanged.
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

    /** The manifest's name + optional SRI digest. */
    record Manifest(String name, String integrity) {}

    private static volatile Manifest manifest;
    private static volatile ClassLoader bundleLoader;

    private PathlandAssets() {}

    /** The current bundle filename (hashed on the demo classpath, literal otherwise). */
    static String bundleName() {
        return manifest().name();
    }

    /** The bundle's SRI digest ({@code sha384-…}), or an empty string when unavailable. */
    static String bundleIntegrity() {
        return manifest().integrity();
    }

    private static Manifest manifest() {
        // A framework host may load this core in a parent extension classloader while
        // the application's static resources live in a child runtime classloader (the
        // Quarkus extension path). Resolve through the thread context classloader first,
        // and keep the cache tied to that loader so a fallback from one loader cannot
        // poison a request served by another.
        ClassLoader loader = contextLoader();
        Manifest cached = manifest;
        if (cached != null && bundleLoader == loader) {
            return cached;
        }
        synchronized (PathlandAssets.class) {
            if (manifest == null || bundleLoader != loader) {
                manifest = resolveManifest(path -> openClasspath(loader, path));
                bundleLoader = loader;
            }
            return manifest;
        }
    }

    /** Forget the cached manifest (tests). */
    static void resetForTests() {
        synchronized (PathlandAssets.class) {
            manifest = null;
            bundleLoader = null;
        }
    }

    private static ClassLoader contextLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : PathlandAssets.class.getClassLoader();
    }

    /** Deterministic resolution against an explicit resource lookup (tests). */
    static String resolve(Lookup lookup) {
        return resolveManifest(lookup).name();
    }

    /** Resolve the name + optional SRI digest against an explicit lookup (tests). */
    static Manifest resolveManifest(Lookup lookup) {
        for (String path : MANIFEST_PATHS) {
            try (InputStream in = lookup.open(path)) {
                if (in == null) {
                    continue;
                }
                List<String> lines = readLines(in, 2);
                String name = lines.isEmpty() ? "" : lines.get(0).trim();
                if (validBundleName(name)) {
                    String integrity = lines.size() > 1 ? lines.get(1).trim() : "";
                    return new Manifest(name, validIntegrity(integrity) ? integrity : "");
                }
            } catch (IOException ignored) {
                // fall through to the fallback
            }
        }
        return new Manifest(FALLBACK, "");
    }

    private static InputStream openClasspath(ClassLoader preferred, String path) {
        InputStream in = preferred == null ? null : preferred.getResourceAsStream(path);
        if (in != null) {
            return in;
        }
        ClassLoader own = PathlandAssets.class.getClassLoader();
        if (own != null && own != preferred) {
            in = own.getResourceAsStream(path);
            if (in != null) {
                return in;
            }
        }
        return ClassLoader.getSystemResourceAsStream(path);
    }

    private static List<String> readLines(InputStream in, int max) throws IOException {
        List<String> lines = new ArrayList<>(max);
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (int i = 0; i < max; i++) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                lines.add(line);
            }
        }
        return lines;
    }

    /** A bare basename: letters, digits, dot, underscore, hyphen — never a path. */
    private static boolean validBundleName(String name) {
        return name != null
                && !name.isEmpty()
                && name.endsWith(".js")
                && name.chars().allMatch(c ->
                        Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-');
    }

    /** A `sha384-<base64>` SRI digest (bounded, base64 alphabet only). */
    private static boolean validIntegrity(String integrity) {
        if (integrity == null || !integrity.startsWith("sha384-")) {
            return false;
        }
        String digest = integrity.substring("sha384-".length());
        return !digest.isEmpty()
                && digest.length() <= 128
                && digest.chars().allMatch(c ->
                        Character.isLetterOrDigit(c) || c == '+' || c == '/' || c == '=');
    }
}
