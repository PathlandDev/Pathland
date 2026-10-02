package com.pathland.demo.assets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The demo's media + icon assets are embedded **once** in this project (the shared
 * {@code pathland-demo-views} jar), served by the web demos from
 * {@code META-INF/resources} and extracted by the GTK/TUI desktop hosts. A
 * missing/renamed asset must fail the build here, not 404 silently in a demo.
 */
class DemoAssetsTest {

    @Test
    void allReferencedDemoAssetsResolveOnTheClasspath() {
        for (String asset : DemoAssets.ASSETS) {
            assertNotNull(DemoAssetsTest.class.getResourceAsStream(DemoAssets.RESOURCE_BASE + asset),
                    "missing shared demo asset: " + asset);
        }
    }

    @Test
    void extractToTempMaterializesEveryAsset() {
        java.nio.file.Path root = DemoAssets.extractToTemp();
        for (String asset : DemoAssets.ASSETS) {
            assertTrue(root.resolve("assets").resolve(asset).toFile().isFile(),
                    "extraction missed: " + asset);
        }
    }
}