package com.pathland.demo.assets;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The demo's media + icon assets are embedded **once** in this project (the shared
 * {@code pathland-demo-views} jar), served by the web demos from
 * {@code META-INF/resources} and extracted by the GTK/TUI desktop hosts. A
 * missing/renamed asset must fail the build here, not 404 silently in a demo.
 */
class DemoAssetsTest {

    private static final String RESOURCE_BASE = "/META-INF/resources/_pathland/assets/";

    /** Every asset path the shared views reference (music + sidebar/kitchensink icons). */
    private static final List<String> ASSETS = List.of(
            "audio/track1.mp3", "audio/track2.mp3", "audio/track3.mp3",
            "audio/track4.mp3", "audio/track5.mp3", "audio/track6.mp3",
            "albumart/cover1.jpg", "albumart/cover2.jpg", "albumart/cover3.jpg",
            "albumart/cover4.jpg", "albumart/cover5.jpg", "albumart/cover6.jpg",
            "icons/home.svg", "icons/kitchen.svg", "icons/settings.svg",
            "icons/save.svg", "icons/cloud.svg", "icons/status.svg",
            "lyrics/Building_on_Solid_Ground.srt", "lyrics/Pathland_Crossing.srt",
            "lyrics/Rendered_Free.srt", "lyrics/Rendered_In_Your_Arms.srt",
            "lyrics/Sixty_Frames_Per_Second.srt", "lyrics/The_Pathland_Dream.srt");

    @Test
    void allReferencedDemoAssetsResolveOnTheClasspath() {
        for (String asset : ASSETS) {
            assertNotNull(DemoAssetsTest.class.getResourceAsStream(RESOURCE_BASE + asset),
                    "missing shared demo asset: " + asset);
        }
    }
}