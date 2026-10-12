package com.pathland.demo.sidebar;

import com.pathland.view.*;
import com.pathland.view.router.Navigation;
import com.pathland.view.router.Router;
import com.pathland.view.signal.Signal;
import static com.pathland.view.Modifiers.*;
import static com.pathland.view.signal.Signals.*;

public class Sidebar implements View {
    // Environment.value returns a lazy signal when the key isn't bound yet — this
    // field is initialized before SplitNavDemo's EnvironmentBinding(ROUTER, router) scope
    // is pushed, so it resolves on .get() inside body(), at render time.
    private final Signal<Router> router = Environment.value(Navigation.ROUTER);
    private static final Color SIDEBAR_BG = Color.rgb(0xF2, 0xF3, 0xF7);
    private static final Color SIDEBAR_BORDER = Color.rgb(0xDD, 0xE0, 0xE8);
    private static final Color ACTIVE_BG = Color.rgb(0xDC, 0xE4, 0xFF);
    private static final Color ACTIVE_FG = Color.rgb(0x1A, 0x3A, 0x8C);

    @Override
    public View body() {
        // The router signal resolves here, inside SplitNavDemo's pushed
        // EnvironmentBinding(Navigation.ROUTER, router) scope.
        return VStack.make(v -> v.alignment(HorizontalAlignment.LEADING).spacing(8)).children(
                        Text.make("Pathland").modifiers(
                                fontSize(18), FontWeight.BOLD),
                        menuRow("/home", "Home", IconName.HOME),
                        menuRow("/kitchen", "Kitchen sink", IconName.GRID),
                        menuRow("/settings", "Settings", IconName.SETTINGS),
Spacer.make().modifiers()
                ).modifiers(padding(16))
                // Fixed-width sidebar with no height hint: as a flex child of the split
                // HStack it stretches to the row's full height (align-items: stretch).
                // Alignment is omitted so the stack's own FILL child-alignment (stretch
                // the menu rows) is preserved.
                .modifiers(frame(f -> f.width(200f)))
                .modifiers(background(SIDEBAR_BG))
                .modifiers(border(b -> b.color(SIDEBAR_BORDER).width(1f)))
                // The sidebar is the app's primary navigation region → a `<nav>` landmark.
                .modifiers(accessibilityRole(Roles.NAVIGATION))
                // Custom-looking buttons: real `Button`s styled with a `ButtonStyle`
                // (control semantics are intrinsic to the component, not a `ROLE`).
                .modifiers(PlainButtonStyle.INSTANCE);
    }

    /** A sidebar menu row (a {@link Label}): navigates the router (direct selection — no
     *  back-stack growth). The sidebar sits outside the {@code NavigationContainer} (it is
     *  the developer's own nav chrome), so it captures the router explicitly — the
     *  nearest-enclosing-router mechanism (spec DSL.md §4.5) resolves intents for
     *  components *inside* a container. */
    private View menuRow(String path, String label, IconName icon) {
        // Reactive active-item highlight via Navigation.isActive: a computed signal from
        // the route signal, so a selection re-emits only this row's background/color.
        var router = this.router.get();
        var active = Navigation.isActive(router, path);
        var bg = computed(() -> active.get() ? ACTIVE_BG : Color.CLEAR);
        var fg = computed(() -> active.get() ? ACTIVE_FG : Color.BLACK);

        // The label is FILL-width so the native button's centered content box is
        // neutralized and the label's own leading alignment positions the row
        // (spec DSL.md §5.7: main-axis alignment is the content's own layout).
        return Button.make(b -> b.action(() -> router.navigate(path))).children(
                Label.make(l -> l.title(label).icon(Icon.make(icon)))
                        .modifiers(background(bg), foregroundStyle(fg))
                        .modifiers(frame(f -> f.width(Commands.Size.FILL)))
        );
    }
}
