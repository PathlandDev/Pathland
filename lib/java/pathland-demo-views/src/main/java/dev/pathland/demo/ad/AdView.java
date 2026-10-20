package dev.pathland.demo.ad;

import dev.pathland.view.*;
import dev.pathland.view.state.State;
import static dev.pathland.view.Modifiers.*;

import static dev.pathland.view.signal.Signals.computed;

/**
 * A small self-contained demo app — a title and a button that reacts to clicks —
 * used as the **origin** of the music player's sponsored proxy slot
 * (spec/PROXY.md).
 *
 * <p>It is mounted as its own Pathland app (e.g. {@code /ad} on the same server)
 * and embedded into the player's Now Playing pane via a {@code ProxyView}.
 * Clicking the button updates this app's own per-session {@link State}; the
 * delta is forwarded through the proxy and applied in place — the embedded app is
 * fully interactive.
 */
public final class AdView implements View {

    /** The per-session click count (wired to the store by the annotation processor). */
    State<Integer> clicks = new State<>(0, "ad.clicks");

    @Override
    public View body() {
        return VStack.make(v -> v.alignment(HorizontalAlignment.CENTER).spacing(10))
                .modifiers(
                        padding(16),
                        background(Color.rgb(0xE8, 0xEF, 0xFF)),
                        cornerRadius(10))
                .children(
                        Text.make("Pathland Ads").modifiers(fontSize(16), FontWeight.BOLD),
                        Text.make("A second app, served on this same server, embedded here via a proxy view.")
                                .modifiers(fontSize(12), foregroundStyle(Color.rgb(0x6B, 0x72, 0x80))),
                        Button.make(b -> b.title("Click me").action(() -> clicks.update(v -> v + 1))),
                        Text.make(computed(() -> clicks.get() == 1
                                        ? "Clicked 1 time"
                                        : "Clicked " + clicks.get() + " times"))
                                .modifiers(fontSize(13), FontWeight.SEMIBOLD));
    }
}
