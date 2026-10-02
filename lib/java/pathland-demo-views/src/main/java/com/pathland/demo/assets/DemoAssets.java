package com.pathland.demo.assets;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * The single embedded copy of the demo's media + icon assets (in this jar under
 * {@code META-INF/resources/_pathland/assets/}). Every host serves them from a
 * temp-dir extraction rather than from inside the jar — jar-based static serving
 * is slow for media (no efficient Range/seek, no OS cache) — so a helper here
 * centralizes the extraction:
 *
 * <ul>
 *   <li>the web demos (Quarkus, Spring) mount their framework static handler on
 *       {@code <root>/assets/…} for {@code /_pathland/assets/**} (Range + caching),</li>
 *   <li>the GTK host sets the returned root as its asset root (the renderer maps
 *       {@code /_pathland/assets/x} → {@code <root>/assets/x}).</li>
 * </ul>
 *
 * The {@code META-INF/resources} copy stays the single source of truth; the temp
 * extraction is a per-boot, transient mirror for fast disk serving.
 */
public final class DemoAssets {

    /** The classpath base of the embedded copy (web-style URL → classpath path). */
    public static final String RESOURCE_BASE = "/META-INF/resources/_pathland/assets/";

    /** Every asset the shared views reference, relative to the {@code assets/} root. */
    public static final List<String> ASSETS = List.of(
            "audio/track1.mp3", "audio/track2.mp3", "audio/track3.mp3",
            "audio/track4.mp3", "audio/track5.mp3", "audio/track6.mp3",
            "albumart/cover1.jpg", "albumart/cover2.jpg", "albumart/cover3.jpg",
            "albumart/cover4.jpg", "albumart/cover5.jpg", "albumart/cover6.jpg",
            "icons/home.svg", "icons/kitchen.svg", "icons/settings.svg",
            "icons/save.svg", "icons/cloud.svg", "icons/status.svg",
            "lyrics/Building_on_Solid_Ground.srt", "lyrics/Pathland_Crossing.srt",
            "lyrics/Rendered_Free.srt", "lyrics/Rendered_In_Your_Arms.srt",
            "lyrics/Sixty_Frames_Per_Second.srt", "lyrics/The_Pathland_Dream.srt");

    private DemoAssets() {
    }

    /**
     * Extract every embedded asset into a fresh temp directory under
     * {@code <root>/assets/…} and return the root. Call once per host at startup;
     * the extracted copy is transient (re-created on each boot).
     */
    public static Path extractToTemp() {
        try {
            Path root = Files.createTempDirectory("pathland-demo-assets");
            extractInto(root);
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException("could not extract demo assets", e);
        }
    }

    /**
     * Extract every embedded asset into {@code <root>/assets/…}, overwriting any
     * stale copy (a previous run's files must never win over this build's).
     */
    public static void extractInto(Path root) {
        try {
            for (String asset : ASSETS) {
                Path target = root.resolve("assets").resolve(asset);
                Files.createDirectories(target.getParent());
                try (InputStream in = DemoAssets.class.getResourceAsStream(RESOURCE_BASE + asset)) {
                    if (in == null) {
                        throw new IllegalStateException("missing shared demo asset: " + asset);
                    }
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not extract demo assets", e);
        }
    }
}