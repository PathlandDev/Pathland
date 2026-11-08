package dev.pathland.server;

/**
 * Read-only build/asset information for diagnostic tooling (the Quarkus Dev UI):
 * the content-hashed DOM client bundle the SSR document references and its SRI
 * digest. Resolved from the same manifest the renderer uses, so the Dev UI shows
 * exactly what the browser is told to load.
 */
public final class PathlandBuildInfo {

    private PathlandBuildInfo() {
    }

    /** The current DOM client bundle filename (e.g. {@code dom-renderer-<hash>.js}). */
    public static String domBundleName() {
        return PathlandAssets.bundleName();
    }

    /** The bundle's Subresource Integrity digest ({@code sha384-…}), or empty. */
    public static String domBundleIntegrity() {
        return PathlandAssets.bundleIntegrity();
    }
}
