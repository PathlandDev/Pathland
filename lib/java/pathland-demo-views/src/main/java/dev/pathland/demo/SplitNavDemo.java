package dev.pathland.demo;

import dev.pathland.demo.home.HomeView;
import dev.pathland.demo.kitchensink.KitchenSinkView;
import dev.pathland.demo.settings.SettingsView;
import dev.pathland.demo.sidebar.Sidebar;
import dev.pathland.view.*;
import dev.pathland.view.router.Navigation;
import dev.pathland.view.router.Router;
import static dev.pathland.view.Modifiers.*;
import static dev.pathland.view.signal.Signals.computed;

/**
 * A master-detail (split) navigation demo (spec PRIMITIVES.md: `NavigationSplitView` →
 * `HStack` sidebar + detail): a fixed **menu column on the left** with three items and
 * a `NavigationContainer` **content area on the right** that swaps on selection. The
 * menu is the developer's own navigation UI; the content area is the navigation slot
 * (`Navigation.container(router)`), so the renderer provides the detail chrome.
 *
 * <p>Routes: {@code /} and {@code /home} (Home), {@code /kitchen} (the full
 * {@link KitchenSinkView} showcase), {@code /settings} (a couple of bound controls), plus
 * a 404 fallback. Built with the {@link Navigation} facade — {@code Navigation.navigator}
 * collapses the route table + router + seeding. The active menu row is highlighted
 * reactively via {@code Navigation.isActive(router, path)} → a computed signal that
 * drives {@code background(b -> b.color(Signal<Color>))} / {@code foregroundStyle(f -> f.color(Signal<Color>))}.
 *
 * <p>The host mounts this view directly with the active platform path provided as an
 * environment value ({@code Platform.ACTIVE_PATH}); {@link #body()} reads it and builds
 * a **bound** router (external path changes are guard-processed, navigation is mirrored
 * back into the signal). The view itself is router-free — no wrapping, no app factory.
 * Content areas are router-free, self-contained views ({@link HomeView},
 * {@link SettingsView}, {@link KitchenSinkView}) instantiated inline in the route table.
 */
public final class SplitNavDemo implements View {

    /**
     * The destinations are constructed **once**, not inline in `body()`.
     * `body()` re-runs whenever the active path changes (it is read to bind the
     * router), and a destination that owns a {@link dev.pathland.view.MediaController}
     * must keep the same controller instance across those re-renders — otherwise a
     * new controller means a new `MEDIA_CONTROLLER` node id and the renderer
     * restarts playback (spec/MEDIA.md).
     */
    private final View home = new HomeView();
    private final View kitchen = new KitchenSinkView();
    private final View settings = new SettingsView();

    @Override
    public View body() {
        // The active platform path is a scoped environment value (provided by the host).
        // Environment.value always returns a signal; the router binds to it (reading
        // external writes and mirroring navigation back when the host signal is writable).
        Router router = Navigation.navigator()
                .route("/", home)
                .route("/home", home)
                .route("/kitchen", kitchen)
                .route("/settings", settings)
                .fallback(Text.make("Not Found"))
                .build(Environment.value(Platform.ACTIVE_PATH));
        // The split: a fixed sidebar column + the structural navigation slot (detail).
        // onPathChange demonstrates observing the active platform path without a router.
        return HStack.make()
                .modifiers(
                        frame(f -> f.width(Float.POSITIVE_INFINITY)),
                        pathChange(p -> p.listener(path -> System.out.println("[split] active path: " + path))),
                        environmentBinding(e -> e.key(Navigation.ROUTER).value(router)),
                        // The route owns the window/tab title; the computed reads the
                        // router signal, so a navigation emits one META opcode.
                        windowMetadata(w -> w
                                .title(computed(() -> switch (router.path()) {
                                    case "/kitchen" -> "Kitchen Sink · Pathland";
                                    case "/settings" -> "Settings · Pathland";
                                    default -> "Home · Pathland";
                                }))
                                .description(computed(() -> switch (router.path()) {
                                    case "/kitchen" -> "The Pathland component showcase";
                                    case "/settings" -> "Pathland demo settings";
                                    default -> "A Pathland split-navigation demo";
                                }))
                                .locale("en")))
                .children(
                        new Sidebar(),
                        ScrollView.make() .modifiers(frame(f -> f.width(Float.POSITIVE_INFINITY)))
                                .children(Navigation.container(router)));
    }

}