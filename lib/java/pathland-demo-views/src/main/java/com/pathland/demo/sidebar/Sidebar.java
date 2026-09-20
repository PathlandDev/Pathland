package com.pathland.demo.sidebar;

import com.pathland.view.*;
import com.pathland.view.router.Navigation;
import com.pathland.view.router.Router;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

public class Sidebar implements View {
    // Environment.value returns a lazy signal when the key isn't bound yet — this
    // field is initialized before SplitNavDemo's `.environment(ROUTER, router)` scope
    // is pushed, so it resolves on .get() inside body(), at render time.
    private final Signal<Router> router = Environment.value(Navigation.ROUTER);
    private static final Color SIDEBAR_BG = Color.rgb(0xF2, 0xF3, 0xF7);
    private static final Color SIDEBAR_BORDER = Color.rgb(0xDD, 0xE0, 0xE8);
    private static final Color ACTIVE_BG = Color.rgb(0xDC, 0xE4, 0xFF);
    private static final Color ACTIVE_FG = Color.rgb(0x1A, 0x3A, 0x8C);

    @Override
    public View body() {
        // The router signal resolves here, inside SplitNavDemo's pushed
        // .environment(Navigation.ROUTER, router) scope.
        return VStack.of(Alignment.FILL, 8,
                        Text.of("Pathland").modifiers(
                                FontSize.of(18), FontWeightMod.of(FontWeight.BOLD)),
                        menuRow("/home", "Home", "icons/home.svg"),
                        menuRow("/kitchen", "Kitchen sink", "icons/kitchen.svg"),
                        menuRow("/settings", "Settings", "icons/settings.svg"),
                        Spacer.of()
                ).modifiers(Padding.of(16))
                // Fixed-width sidebar with no height hint: as a flex child of the split
                // HStack it stretches to the row's full height (align-items: stretch).
                // Alignment is omitted so the stack's own FILL child-alignment (stretch
                // the menu rows) is preserved.
                .modifier(FrameMod.of(200f))
                .modifier(Background.of(SIDEBAR_BG))
                .modifier(Border.of(SIDEBAR_BORDER, 1f));
    }

    /** A sidebar menu row (a {@link Label}): navigates the router (direct selection — no
     *  back-stack growth). The sidebar sits outside the {@code NavigationContainer} (it is
     *  the developer's own nav chrome), so it captures the router explicitly — the
     *  nearest-enclosing-router mechanism (spec DSL.md §4.5) resolves intents for
     *  components *inside* a container. */
    private View menuRow(String path, String label, String icon) {
        // Reactive active-item highlight via Navigation.isActive: a computed signal from
        // the route signal, so a selection re-emits only this row's background/color.
        var router = this.router.get();
        Signal<Boolean> active = Navigation.isActive(router, path);
        Signal<Color> bg = Signals.computed(() -> active.get() ? ACTIVE_BG : Color.CLEAR);
        Signal<Color> fg = Signals.computed(() -> active.get() ? ACTIVE_FG : Color.BLACK);

        return Label.of(label, icon)
                .modifiers(
                        TapGesture.of(() -> router.navigate(path)),
                        Background.of(bg), ForegroundStyle.of(fg)
                );
        //return Button.of(label, () -> router.navigate(path))
        //        .modifiers(Background.of(bg), ForegroundStyle.of(fg));
    }
}
