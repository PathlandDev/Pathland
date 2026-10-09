package com.pathland.view.router;

import com.pathland.view.Components;
import com.pathland.view.Configurable;
import com.pathland.view.Environment;
import com.pathland.view.EnvironmentValues;
import com.pathland.view.Properties;
import com.pathland.view.View;
import com.pathland.view.ViewBuilder;
import com.pathland.view.ViewModifier;
import com.pathland.view.emit.PathlandNode;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The navigation slot (spec DSL.md §4.5): a structural container
 * (spec DSL.md §3.4) whose single child is the current route's destination. On a
 * route change the emitter reconciles the slot — emitting only {@code TREE} deltas
 * plus the {@code ROUTE} property in the same frame (the DOM client reacts with
 * {@code history.pushState}). The slot materializes as a {@code Group} (a bare
 * {@code VSTACK} node with no spacing/alignment).
 *
 * <p>{@code NavigationContainer.with(n -> n.router(router))} (see {@link Chrome} for
 * the chrome-mode choice). The container also registers its router's {@code NAVIGATE}
 * handler, which the emitter surfaces on {@code RenderResult.navigateHandler} so a
 * host can forward raw {@code NAVIGATE} events (native back, browser {@code popstate})
 * straight into the router.
 */
public final class NavigationContainer implements View, Configurable<NavigationContainer.Config> {

    /** {@link NavigationContainer} values. */
    public static final class Config implements View.Config {

        private Router router;
        private Chrome chrome = Chrome.PLATFORM_DEFAULT;

        /** The router the slot renders. */
        public Config router(Router router) {
            this.router = router;
            return this;
        }

        /** The chrome mode ({@link Chrome}). */
        public Config chrome(Chrome chrome) {
            this.chrome = chrome;
            return this;
        }
    }

    private final Config config;

    private NavigationContainer(Config config) {
        this.config = config;
    }

    /** Configure the navigation slot ({@code NavigationContainer.with(n -> n.router(router))}). */
    public static ViewBuilder<NavigationContainer, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new NavigationContainer(config));
    }

    /** Apply modifiers to a bare navigation slot. */
    public static ViewBuilder<NavigationContainer, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new NavigationContainer(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        Router router = Objects.requireNonNull(config.router, "router");
        Chrome chrome = Objects.requireNonNull(config.chrome, "chrome");
        PathlandNode node = new PathlandNode(Components.VSTACK); // Group-backed slot
        node.structuralContent = router::destination; // reads the route signal (tracked)
        // Scope the router to this subtree (a component inside a destination reads
        // `env.value(Navigation.ROUTER)`), and capture the scope the destination should
        // see so `Emitter.reconcileSlot` re-applies it on every re-render.
        EnvironmentValues scope = Environment.current().with(Navigation.ROUTER, router);
        node.environmentForChildren = scope;
        EnvironmentValues previous = Environment.current();
        Environment.within(scope);
        try {
            View selected = router.destination();
            if (selected != null) {
                node.children.add(selected.render(env));
            }
        } finally {
            Environment.restore(previous);
        }
        // ROUTE: the current path, re-emitted by the structural effect in the same frame
        // as a destination swap (spec DSL.md §4.5 URL sync). NAV_DEPTH: the back-stack
        // depth, so native navigation adapters reconcile their page stack by depth.
        // TRANSITION: a presentation hint (PlatformDefault) so renderers may animate
        // the swap — never state.
        node.properties.put(Properties.ROUTE, router.path());
        node.structuralStringProperty = Properties.ROUTE;
        node.structuralStringValue = router::path;
        node.properties.put(Properties.NAV_DEPTH, router.depth());
        node.structuralU32Property = Properties.NAV_DEPTH;
        node.structuralU32Value = router::depth;
        // Chrome mode: a static property (never varies per destination) — the
        // renderer supplies default chrome, or the developer owns all nav UI.
        node.properties.put(Properties.NAV_CHROME, (float) chrome.wire());
        node.properties.put(Properties.TRANSITION, 1f); // PlatformDefault
        node.navigateHandler = router::handleEvent;
        node.router = router; // the nearest-enclosing router for declarative route intents
        return node;
    }
}