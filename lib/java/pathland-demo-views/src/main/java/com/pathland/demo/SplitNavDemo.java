package com.pathland.demo;

import com.pathland.demo.home.HomeView;
import com.pathland.demo.kitchensink.KitchenSinkView;
import com.pathland.demo.settings.SettingsView;
import com.pathland.demo.sidebar.Sidebar;
import com.pathland.view.*;
import com.pathland.view.router.Navigation;
import com.pathland.view.router.Router;

/**
 * A master-detail (split) navigation demo (spec PRIMITIVES.md: `NavigationSplitView` →
 * `HStack` sidebar + detail): a fixed **menu column on the left** with three items and
 * a `NavigationContainer` **content area on the right** that swaps on selection. The
 * menu is the developer's own navigation UI; the content area is the navigation slot
 * (`Navigation.of(router)`), so the renderer provides the detail chrome.
 *
 * <p>Routes: {@code /} and {@code /home} (Home), {@code /kitchen} (the full
 * {@link KitchenSinkView} showcase), {@code /settings} (a couple of bound controls), plus
 * a 404 fallback. Built with the {@link Navigation} facade — {@code Navigation.navigator}
 * collapses the route table + router + seeding. The active menu row is highlighted
 * reactively via {@code Navigation.isActive(router, path)} → a computed signal that
 * drives {@code Background.of(Signal<Color>)} / {@code ForegroundStyle.of(Signal<Color>)}.
 *
 * <p>The host mounts this view directly with the active platform path provided as an
 * environment value ({@code Platform.ACTIVE_PATH}); {@link #body()} reads it and builds
 * a **bound** router (external path changes are guard-processed, navigation is mirrored
 * back into the signal). The view itself is router-free — no wrapping, no app factory.
 * Content areas are router-free, self-contained views ({@link HomeView},
 * {@link SettingsView}, {@link KitchenSinkView}) instantiated inline in the route table.
 */
public final class SplitNavDemo implements View {


    @Override
    public View body() {
        // The active platform path is a scoped environment value (provided by the host).
        // Environment.value always returns a signal; the router binds to it (reading
        // external writes and mirroring navigation back when the host signal is writable).
        Router router = Navigation.navigator()
                .route("/", new HomeView())
                .route("/home", new HomeView())
                .route("/kitchen", new KitchenSinkView())
                .route("/settings", new SettingsView())
                .fallback(Text.of("Not Found"))
                .build(Environment.value(Platform.ACTIVE_PATH));
        // The split: a fixed sidebar column + the structural navigation slot (detail).
        // onPathChange demonstrates observing the active platform path without a router.
        return HStack.of(Alignment.FILL,0,
                        new Sidebar(),
                        ScrollView.of(Navigation.of(router))
                )
                .onPathChange(path -> System.out.println("[split] active path: " + path))
                .environment(Navigation.ROUTER, router);
    }

}