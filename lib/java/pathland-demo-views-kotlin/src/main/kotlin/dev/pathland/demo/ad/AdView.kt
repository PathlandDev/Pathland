package dev.pathland.demo.ad

import dev.pathland.view.core.Color
import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.dsl.Button
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.background
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.cornerRadius
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.fontWeight
import dev.pathland.view.dsl.foregroundStyle
import dev.pathland.view.dsl.padding

/**
 * A small self-contained demo app — a title and a button that reacts to clicks —
 * used as the **origin** of the music player's sponsored proxy slot
 * (spec/PROXY.md).
 *
 * It is mounted as its own Pathland app (e.g. `/ad` on the same server) and
 * embedded into the player's Now Playing pane via a `ProxyView`. Clicking the
 * button updates this app's own per-session state; the delta is forwarded
 * through the proxy and applied in place — the embedded app is fully
 * interactive.
 *
 * A Java host mounts it with `new AdView()` or `AdView.make()`.
 */
class AdView : Component() {

    /** The per-session click count (persisted under an explicit store key). */
    private var clicks by state(0, "ad.clicks")

    override fun body(): View =
        VStack(alignment = HorizontalAlignment.CENTER, spacing = 10f) {
            Text("Pathland Ads")
                .fontSize(16f)
                .fontWeight(FontWeight.BOLD)
            Text("A second app, served on this same server, embedded here via a proxy view.")
                .fontSize(12f)
                .foregroundStyle(Color.rgb(0x4B, 0x55, 0x63))
            Button("Click me") { clicks += 1 }
            Text(computed {
                if (clicks == 1) "Clicked 1 time" else "Clicked $clicks times"
            }).fontSize(13f).fontWeight(FontWeight.SEMIBOLD)
        }
        .padding(16f)
        .background(Color.rgb(0xE8, 0xEF, 0xFF))
        .cornerRadius(10f)

    companion object {
        /** Java-compatible factory. */
        @JvmStatic
        fun make(): AdView = AdView()
    }
}
