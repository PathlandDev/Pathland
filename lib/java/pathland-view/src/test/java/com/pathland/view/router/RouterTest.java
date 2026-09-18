package com.pathland.view.router;

import com.pathland.view.BorderedButtonStyle;
import com.pathland.view.Button;
import com.pathland.view.ButtonStyle;
import com.pathland.view.ButtonStyleMod;
import com.pathland.view.Categories;
import com.pathland.view.Commands;
import com.pathland.view.Environment;
import com.pathland.view.PlainButtonStyle;
import com.pathland.view.Properties;
import com.pathland.view.Text;
import com.pathland.view.ValueTypes;
import com.pathland.view.View;
import com.pathland.view.emit.Emitter;
import com.pathland.view.emit.Frame;
import com.pathland.view.emit.FrameOpcodeSink;
import com.pathland.view.emit.Opcode;
import com.pathland.view.emit.RenderResult;
import com.pathland.view.transport.Event;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The router (spec DSL.md §4.5): a {@code NavigationContainer} is a structural
 * container over the router's route signal — a navigation emits destination
 * {@code TREE} deltas plus the {@code ROUTE} property for web URL sync, params and
 * guards resolve through the {@code RouteTable}, and raw {@code NAVIGATE} events
 * (native back / browser {@code popstate}) route straight into the router.
 */
class RouterTest {

    private static String lastText(Frame frame) {
        Opcode text = frame.opcodes().stream()
                .filter(o -> o.category() == Categories.STYLE && o.command() == Commands.Style.SET_TEXT)
                .reduce((a, b) -> b)
                .orElseThrow();
        return frame.stringAt(text.b());
    }

    private static long countOps(Frame frame, int category, int command) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == category && o.command() == command)
                .count();
    }

    /** The last NAV_DEPTH SET_PROPERTY value in a frame (the current back-stack depth). */
    private static int lastNavDepth(Frame frame) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == Categories.STYLE
                        && o.command() == Commands.Style.SET_PROPERTY
                        && (o.b() & 0xFFFF) == Properties.NAV_DEPTH)
                .reduce((a, b) -> b)
                .orElseThrow().c();
    }

    /** The NAV_CHROME enum code in a frame (F32 bits; PlatformDefault=0, Custom=1). */
    private static float lastNavChrome(Frame frame) {
        return Float.intBitsToFloat(frame.opcodes().stream()
                .filter(o -> o.category() == Categories.STYLE
                        && o.command() == Commands.Style.SET_PROPERTY
                        && (o.b() & 0xFFFF) == Properties.NAV_CHROME)
                .findFirst().orElseThrow().c());
    }

    private static FrameOpcodeSink sink() {
        return new FrameOpcodeSink();
    }

    /** The router seeded the way a host seeds it before mount: {@code navigate(initialPath)}. */
    private static Router seeded(String path) {
        Router router = new Router(table());
        router.navigate(path);
        return router;
    }

    private static RouteTable table() {
        return RouteTable.builder()
                .route("/", params -> ViewStack.of(Text.of("Home")))
                .route("/users", params -> ViewStack.of(Text.of("Users")))
                .route("/users/:id", params -> Text.of("User " + params.get("id")))
                .route("/admin", params -> false, "/", params -> ViewStack.of(Text.of("Admin")))
                .fallback(params -> Text.of("Not Found"))
                .build();
    }

    /** A VSTACK-rooted destination (distinct component type from a plain Text root). */
    private record ViewStack(View child) implements View {
        static ViewStack of(View child) {
            return new ViewStack(child);
        }

        @Override
        public com.pathland.view.emit.PathlandNode render(Environment env) {
            com.pathland.view.emit.PathlandNode node =
                    new com.pathland.view.emit.PathlandNode(com.pathland.view.Components.VSTACK);
            node.children.add(child.render(env));
            return node;
        }
    }

    @Test
    void mountRendersInitialDestinationAndEmitsRoute() {
        Router router = new Router(table());
        FrameOpcodeSink sink = sink();
        RenderResult result = new Emitter(sink).mount(NavigationContainer.of(router), Environment.DEFAULT);

        Frame frame = sink.frame();
        Opcode route = frame.opcodes().stream()
                .filter(o -> o.category() == Categories.STYLE
                        && o.command() == Commands.Style.SET_PROPERTY
                        && (o.b() & 0xFFFF) == Properties.ROUTE)
                .findFirst().orElseThrow();
        assertEquals(ValueTypes.STRING, (route.b() >>> 16) & 0xFF, "ROUTE is a STRING property");
        assertEquals("/", frame.stringAt(route.c()), "the initial path is emitted");
        assertNotNull(result.navigateHandler(), "the container exposes its router's NAVIGATE sink");
    }

    @Test
    void hostSeedsTheInitialRouteBeforeMount() {
        // On SSR the host seeds the router with the request URL before mount, so the
        // first frame renders the right destination with no spurious navigation.
        FrameOpcodeSink sink = sink();
        new Emitter(sink).mount(NavigationContainer.of(seeded("/users")), Environment.DEFAULT);

        Frame frame = sink.frame();
        assertEquals("Users", lastText(frame), "the host-seeded initial route rendered");
        Opcode route = frame.opcodes().stream()
                .filter(o -> o.category() == Categories.STYLE
                        && o.command() == Commands.Style.SET_PROPERTY
                        && (o.b() & 0xFFFF) == Properties.ROUTE)
                .findFirst().orElseThrow();
        assertEquals("/users", frame.stringAt(route.c()));
    }

    @Test
    void initialUrlGuardRedirectsOnTheFirstFrame() {
        // The host seeds through navigate(...), so a request to a guarded URL resolves
        // its redirect BEFORE mount — the first frame shows the redirect target, not the
        // guarded destination.
        FrameOpcodeSink sink = sink();
        new Emitter(sink).mount(NavigationContainer.of(seeded("/admin")), Environment.DEFAULT);

        Frame frame = sink.frame();
        assertEquals("Home", lastText(frame), "a failing guard on the initial URL redirects");
        Opcode route = frame.opcodes().stream()
                .filter(o -> o.category() == Categories.STYLE
                        && o.command() == Commands.Style.SET_PROPERTY
                        && (o.b() & 0xFFFF) == Properties.ROUTE)
                .findFirst().orElseThrow();
        assertEquals("/", frame.stringAt(route.c()), "ROUTE carries the redirect target");
    }

    @Test
    void navigateSwapsDestinationAndEmitsTheRouteProperty() {
        Router router = new Router(table());
        FrameOpcodeSink sink = sink();
        new Emitter(sink).mount(NavigationContainer.of(router), Environment.DEFAULT);

        router.navigate("/users");
        Frame delta = sink.frame();

        // Structural swap (VSTACK home -> VSTACK users; the text child differs) plus ROUTE.
        assertTrue(countOps(delta, Categories.STYLE, Commands.Style.SET_PROPERTY) >= 1);
        boolean sawRoute = false;
        for (Opcode op : delta.opcodes()) {
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_PROPERTY
                    && (op.b() & 0xFFFF) == Properties.ROUTE) {
                assertEquals("/users", delta.stringAt(op.c()));
                sawRoute = true;
            }
        }
        assertTrue(sawRoute, "navigating re-emits the ROUTE property");
        assertTrue(countOps(delta, Categories.STYLE, Commands.Style.SET_TEXT) >= 1,
                "the destination text is re-emitted");
    }

    @Test
    void navigateToDifferentComponentEmitsTreeDeltas() {
        RouteTable table = RouteTable.builder()
                .route("/", params -> ViewStack.of(Text.of("Home")))
                .route("/plain", params -> Text.of("Plain"))
                .build();
        Router router = new Router(table);
        FrameOpcodeSink sink = sink();
        new Emitter(sink).mount(NavigationContainer.of(router), Environment.DEFAULT);

        router.navigate("/plain"); // VSTACK destination -> TEXT destination
        Frame delta = sink.frame();
        assertEquals(1, countOps(delta, Categories.TREE, Commands.Tree.REMOVE_CHILD));
        assertEquals(1, countOps(delta, Categories.TREE, Commands.Tree.DELETE_NODE));
        assertEquals(1, countOps(delta, Categories.TREE, Commands.Tree.CREATE_NODE));
        assertEquals(1, countOps(delta, Categories.TREE, Commands.Tree.INSERT_CHILD));
    }

    @Test
    void pushAndPopManageTheBackStack() {
        Router router = new Router(table());
        FrameOpcodeSink sink = sink();
        new Emitter(sink).mount(NavigationContainer.of(router), Environment.DEFAULT);

        router.push("/users");
        router.push("/users/42");
        assertEquals("/users/42", router.current().path());

        router.pop();
        assertEquals("/users", router.current().path());
        router.back();
        assertEquals("/", router.current().path());
        router.pop(); // empty stack: no-op, stays on the initial route
        assertEquals("/", router.current().path());
    }

    @Test
    void navDepthTracksTheBackStack() {
        Router router = new Router(table());
        FrameOpcodeSink sink = sink();
        new Emitter(sink).mount(NavigationContainer.of(router), Environment.DEFAULT);

        assertEquals(1, lastNavDepth(sink.frame()), "initial navigate is depth 1");
        assertEquals(ValueTypes.U32,
                (sink.frame().opcodes().stream()
                        .filter(o -> (o.b() & 0xFFFF) == Properties.NAV_DEPTH)
                        .findFirst().orElseThrow().b() >>> 16) & 0xFF,
                "NAV_DEPTH is a U32 property");

        router.push("/users");
        assertEquals(2, lastNavDepth(sink.frame()), "push increments depth");
        router.push("/users/42");
        assertEquals(3, lastNavDepth(sink.frame()), "second push increments depth");
        router.pop();
        assertEquals(2, lastNavDepth(sink.frame()), "pop decrements depth");
        router.replace("/users");
        assertEquals(2, lastNavDepth(sink.frame()), "replace keeps depth");
        router.pop();
        assertEquals(1, lastNavDepth(sink.frame()), "back to the root is depth 1");
    }

    @Test
    void chromeModeIsEmittedOnceAtMount() {
        FrameOpcodeSink defaultSink = sink();
        new Emitter(defaultSink).mount(
                NavigationContainer.of(new Router(table())), Environment.DEFAULT);
        assertEquals(0f, lastNavChrome(defaultSink.frame()), "default chrome → PlatformDefault (0)");

        FrameOpcodeSink customSink = sink();
        new Emitter(customSink).mount(
                NavigationContainer.of(new Router(table()), Chrome.CUSTOM), Environment.DEFAULT);
        assertEquals(1f, lastNavChrome(customSink.frame()), "Chrome.CUSTOM → Custom (1)");
    }

    @Test
    void routeParamsReachTheDestination() {
        Router router = new Router(table());
        FrameOpcodeSink sink = sink();
        new Emitter(sink).mount(NavigationContainer.of(router), Environment.DEFAULT);

        router.navigate("/users/42");
        Frame delta = sink.frame();
        boolean sawUser = false;
        for (Opcode op : delta.opcodes()) {
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_TEXT
                    && "User 42".equals(delta.stringAt(op.b()))) {
                sawUser = true;
            }
        }
        assertTrue(sawUser, "the :id param reached the destination factory");
    }

    @Test
    void guardRedirectsInsteadOfMounting() {
        Router router = new Router(table());
        new Emitter(sink()).mount(NavigationContainer.of(router), Environment.DEFAULT);

        router.navigate("/admin"); // guard always fails -> redirect "/"
        assertEquals("/", router.current().path(), "a failing guard replaces to the redirect target");
    }

    @Test
    void fallbackServesUnknownPaths() {
        Router router = new Router(table());
        FrameOpcodeSink sink = sink();
        new Emitter(sink).mount(NavigationContainer.of(router), Environment.DEFAULT);

        router.navigate("/no-such-path");
        Frame delta = sink.frame();
        boolean sawNotFound = false;
        for (Opcode op : delta.opcodes()) {
            if (op.category() == Categories.STYLE && op.command() == Commands.Style.SET_TEXT
                    && "Not Found".equals(delta.stringAt(op.b()))) {
                sawNotFound = true;
            }
        }
        assertTrue(sawNotFound, "the fallback handler serves unmatched paths");
    }

    @Test
    void navigateEventRoutesIntoTheRouter() {
        Router router = new Router(table());
        FrameOpcodeSink sink = sink();
        RenderResult result = new Emitter(sink).mount(NavigationContainer.of(router), Environment.DEFAULT);

        assertNotNull(result.navigateHandler(), "NAVIGATE sink exposed on RenderResult");

        result.navigateHandler().accept(Event.navigate("https://example.com/users/7"));
        assertEquals("/users/7", router.current().path(), "a URL NAVIGATE routes to the path");

        router.push("/users");
        assertEquals("/users", router.current().path());
        result.navigateHandler().accept(Event.navigateBack());
        assertEquals("/users/7", router.current().path(), "a no-URL NAVIGATE pops the back-stack");
    }

    @Test
    void navigationLinkPushesThroughTheTapRegistry() {
        Router router = new Router(table());
        FrameOpcodeSink sink = sink();
        View root = new View() {
            @Override
            public com.pathland.view.emit.PathlandNode render(Environment env) {
                com.pathland.view.emit.PathlandNode node =
                        new com.pathland.view.emit.PathlandNode(com.pathland.view.Components.VSTACK);
                node.children.add(NavigationLink.of("Users", router, "/users").render(env));
                node.children.add(NavigationContainer.of(router).render(env));
                return node;
            }
        };
        RenderResult result = new Emitter(sink).mount(root, Environment.DEFAULT);

        assertTrue(result.tapActions().size() >= 1, "the link exposes a tap action");
        result.tapActions().values().iterator().next().run();
        assertEquals("/users", router.current().path(), "the link pushed the route");
    }

    @Test
    void navigatingToTheCurrentPathIsEqualitySuppressed() {
        Router router = new Router(table());
        FrameOpcodeSink sink = sink();
        new Emitter(sink).mount(NavigationContainer.of(router), Environment.DEFAULT);
        int frames = sink.framesProduced();

        router.navigate("/");
        assertEquals(frames, sink.framesProduced(), "navigating to the same route emits nothing");
    }

    @Test
    void pathOnlyStripsQueryAndFragment() {
        assertEquals("/users/42", Route.of("/users/42?tab=profile#top").pathOnly());
    }

    @Test
    void declarativeNavigateResolvesToTheNearestRouter() {
        // A component inside a destination declares `.navigate`; the emitter resolves it
        // to the enclosing NavigationContainer's router and exposes it in
        // RenderResult.navigateActions — no router is threaded by hand (spec DSL.md §4.5).
        Router router = new Router(RouteTable.builder()
                .route("/", p -> Button.of("Go", () -> {}).navigate("/users"))
                .route("/users", p -> Text.of("Users"))
                .build());
        FrameOpcodeSink sink = sink();
        RenderResult result = new Emitter(sink).mount(NavigationContainer.of(router), Environment.DEFAULT);

        assertTrue(result.navigateActions().size() >= 1, "the declarative intent is registered");
        result.navigateActions().values().iterator().next().run();
        assertEquals("/users", router.current().path(), ".navigate reached the enclosing router");
    }

    @Test
    void declarativePushGrowsTheBackStackAndReplaceDoesNot() {
        Router router = new Router(RouteTable.builder()
                .route("/", p -> Button.of("P", () -> {}).push("/users"))
                .route("/users", p -> Button.of("R", () -> {}).replace("/users/42"))
                .route("/users/42", p -> Text.of("User 42"))
                .build());
        FrameOpcodeSink sink = sink();
        RenderResult result = new Emitter(sink).mount(NavigationContainer.of(router), Environment.DEFAULT);

        // PUSH first: pushes the root, moves to /users (depth 2).
        result.navigateActions().values().iterator().next().run();
        assertEquals("/users", router.current().path());
        assertEquals(2, router.depth(), ".push grows the back-stack");

        // REPLACE: swaps to /users/42 without a back-stack entry (depth stays 2).
        result.navigateActions().values().iterator().next().run();
        assertEquals("/users/42", router.current().path(), ".replace reached /users/42");
        assertEquals(2, router.depth(), ".replace does not grow the back-stack");
    }

    @Test
    void routerAgnosticNavigationLinkRegistersANavIntent() {
        // NavigationLink.of(label, to) without a Router is a PUSH intent resolved to the
        // nearest enclosing router (the existing explicit-router overloads still work).
        Router router = new Router(RouteTable.builder()
                .route("/", p -> NavigationLink.of("Users", "/users"))
                .route("/users", p -> Text.of("Users"))
                .build());
        FrameOpcodeSink sink = sink();
        RenderResult result = new Emitter(sink).mount(NavigationContainer.of(router), Environment.DEFAULT);

        assertTrue(result.navigateActions().size() >= 1, "the router-agnostic link registers a nav intent");
        result.navigateActions().values().iterator().next().run();
        assertEquals("/users", router.current().path(), "the agnostic link pushed via the enclosing router");
        assertEquals(2, router.depth(), "the agnostic link pushes (back-stack grows)");
    }

    @Test
    void navigationFacadeBuildsAndSeedsARouter() {
        // Navigation.navigator collapses the route table + router + seeding (spec DSL.md §4.5).
        int[] seenId = {-1};
        Router router = Navigation.navigator("/kitchen")
                .route("/", Text.of("Home"))
                .route("/kitchen", Text.of("Kitchen"))
                .route("/settings", Text.of("Settings"))
                .route("/users/:id", params -> {
                    seenId[0] = params.intValue("id");
                    return Text.of("User " + params.intValue("id"));
                })
                .fallback(Text.of("Not Found"))
                .build();

        assertEquals("/kitchen", router.path(), "the initial path is seeded before mount");
        assertNotNull(router.destination(), "a destination resolves for the seeded route");

        router.navigate("/users/42");
        assertEquals("/users/42", router.path());
        assertNotNull(router.destination(), "the destination resolves for the new route");
        assertEquals(42, seenId[0], "typed params reach the destination factory");
    }

    @Test
    void paramsOfferTypedAccess() {
        Params params = Params.of(Map.of("id", "42", "size", "10", "on", "true", "pi", "3.5"), "/users/42");
        assertEquals("42", params.get("id"));
        assertEquals(42, params.intValue("id"));
        assertEquals(10L, params.longValue("size"));
        assertTrue(params.booleanValue("on"));
        assertEquals(3.5, params.doubleValue("pi"), 0.001);
        assertEquals("/users/42", params.path());
        assertEquals(0, params.intValue("missing"));
        assertEquals(0.0, params.doubleValue("missing"), 0.001);
        assertTrue(Params.none().isEmpty());
    }

    @Test
    void navigationIsActiveTracksTheRouteSignal() {
        Router router = Navigation.navigator("/home")
                .route("/home", Text.of("Home"))
                .route("/settings", Text.of("Settings"))
                .build();
        com.pathland.view.signal.Signal<Boolean> onHome = Navigation.isActive(router, "/home");
        com.pathland.view.signal.Signal<Boolean> onSettings = Navigation.isActive(router, "/settings");

        assertTrue(onHome.get(), "the current route is active");
        assertEquals(false, onSettings.get(), "a different route is not active");

        router.navigate("/settings");
        assertEquals(false, onHome.get(), "isActive re-evaluates when the route changes");
        assertTrue(onSettings.get(), "the new route becomes active");
    }

    @Test
    void environmentScopingIsHierarchicalAndScoped() {
        // A scoped `.environment(key, value)` is active only for its subtree; the
        // innermost binding wins, and the value is restored after render.
        com.pathland.view.EnvironmentKey<String> key = com.pathland.view.EnvironmentKey.of("k");
        java.util.List<String> seen = new java.util.ArrayList<>();

        View probe = new View() {
            @Override
            public com.pathland.view.emit.PathlandNode render(Environment env) {
                seen.add(com.pathland.view.Environment.value(key).get());
                return new com.pathland.view.emit.PathlandNode(com.pathland.view.Components.TEXT);
            }
        };

        // innermost first: `.environment(k, "inner")` applied innermost → nearest wins.
        View innerWins = probe.environment(key, "inner").environment(key, "outer");
        View outerOnly = probe.environment(key, "outer");

        // Render twice: the scope must not leak between renders (restore).
        new Emitter(sink()).mount(innerWins, Environment.DEFAULT);
        new Emitter(sink()).mount(outerOnly, Environment.DEFAULT);

        assertEquals("inner", seen.get(0), "nearest (innermost) binding wins");
        assertEquals("outer", seen.get(1), "the outer binding applies without an inner override");
        assertEquals(null, com.pathland.view.Environment.value(key).get(), "no leak after render (restored)");
    }

    @Test
    void environmentValueReturnsSignal() {
        // Environment.value always returns a signal: a plain injected value is wrapped
        // (so `.get()` gives the value), and an injected signal comes back as the same
        // instance (so a node bound to it stays reactive).
        com.pathland.view.EnvironmentKey<String> plainKey = com.pathland.view.EnvironmentKey.of("plain");
        com.pathland.view.EnvironmentKey<String> sigKey = com.pathland.view.EnvironmentKey.of("sig");
        com.pathland.view.signal.WritableSignal<String> host =
                com.pathland.view.signal.Signals.signal("/");
        java.util.List<String> plainSeen = new java.util.ArrayList<>();
        java.util.List<com.pathland.view.signal.Signal<String>> sigSeen = new java.util.ArrayList<>();

        View probe = new View() {
            @Override
            public com.pathland.view.emit.PathlandNode render(Environment env) {
                plainSeen.add(com.pathland.view.Environment.value(plainKey).get());
                sigSeen.add(com.pathland.view.Environment.value(sigKey));
                return new com.pathland.view.emit.PathlandNode(com.pathland.view.Components.TEXT);
            }
        };
        View root = probe
                .environment(plainKey, "hello")
                .environment(sigKey, host);

        new Emitter(sink()).mount(root, Environment.DEFAULT);
        assertEquals("hello", plainSeen.get(0), "a plain value is wrapped and readable via .get()");
        assertSame(host, sigSeen.get(0), "an injected signal is returned as the same instance (reactive)");

        // Absent key: the read still yields a signal whose .get() is null.
        java.util.List<String> missing = new java.util.ArrayList<>();
        View missingProbe = new View() {
            @Override
            public com.pathland.view.emit.PathlandNode render(Environment env) {
                missing.add(com.pathland.view.Environment.value(plainKey).get());
                return new com.pathland.view.emit.PathlandNode(com.pathland.view.Components.TEXT);
            }
        };
        new Emitter(sink()).mount(missingProbe, Environment.DEFAULT);
        assertEquals(null, missing.get(0), "no binding → a constant signal yielding null");
    }

    @Test
    void environmentValueReadsInsideBody() {
        // A composite reads the injected router in body(), via Environment.value(.get()).
        Router router = Navigation.navigator().route("/", Text.of("x")).build();
        java.util.List<Router> seen = new java.util.ArrayList<>();

        View composite = new View() {
            @Override
            public View body() {
                seen.add(com.pathland.view.Environment.value(Navigation.ROUTER).get());
                return Text.of("x");
            }
        };

        new Emitter(sink()).mount(composite.environment(Navigation.ROUTER, router), Environment.DEFAULT);
        assertEquals(1, seen.size());
        assertEquals(router, seen.get(0), "the env value is visible through the signal read");

        // Without the modifier the read yields null (no scope binding).
        seen.clear();
        new Emitter(sink()).mount(composite, Environment.DEFAULT);
        assertEquals(1, seen.size());
        assertEquals(null, seen.get(0), "no binding → null");
    }

    @Test
    void environmentValueFieldResolvesLazilyAtRenderTime() {
        // The @Environment field style: Environment.value is read in a field initializer,
        // BEFORE the enclosing `.environment(...)` scope is pushed, so it returns a lazy
        // signal that captures the binding when .get() runs inside body(), during render.
        Router router = Navigation.navigator().route("/", Text.of("x")).build();
        java.util.List<Router> seen = new java.util.ArrayList<>();

        class SidebarLike implements View {
            private final com.pathland.view.signal.Signal<Router> routerField =
                    com.pathland.view.Environment.value(Navigation.ROUTER);

            @Override
            public View body() {
                seen.add(routerField.get());
                return Text.of("x");
            }
        }

        new Emitter(sink()).mount(new SidebarLike().environment(Navigation.ROUTER, router), Environment.DEFAULT);
        assertEquals(1, seen.size());
        assertEquals(router, seen.get(0), "a field read before the scope resolves at render time");

        // Reuse the same view instance: the lazy signal re-captures on the next render.
        seen.clear();
        Router other = Navigation.navigator().route("/", Text.of("y")).build();
        new Emitter(sink()).mount(new SidebarLike().environment(Navigation.ROUTER, other), Environment.DEFAULT);
        assertEquals(1, seen.size());
        assertEquals(other, seen.get(0), "a fresh render under a different binding resolves it");

        // Without any binding the field resolves to null.
        seen.clear();
        new Emitter(sink()).mount(new SidebarLike(), Environment.DEFAULT);
        assertEquals(1, seen.size());
        assertEquals(null, seen.get(0), "no binding → the lazy field yields null");
    }

    @Test
    void buttonStyleIsAKeyedEnvironmentValue() {
        // A `.buttonStyle(...)` scope rides the generic environment: the style is the
        // `Environment.BUTTON_STYLE` keyed value — readable via Environment.value inside
        // the subtree, absent outside it.
        java.util.List<ButtonStyle> seen = new java.util.ArrayList<>();
        View probe = new View() {
            @Override
            public com.pathland.view.emit.PathlandNode render(Environment env) {
                seen.add(env.buttonStyle());
                seen.add(com.pathland.view.Environment.value(Environment.BUTTON_STYLE).get());
                return new com.pathland.view.emit.PathlandNode(com.pathland.view.Components.TEXT);
            }
        };

        new Emitter(sink()).mount(
                probe.modifier(ButtonStyleMod.of(BorderedButtonStyle.INSTANCE)),
                Environment.DEFAULT);
        assertEquals(BorderedButtonStyle.INSTANCE, seen.get(0), "buttonStyle() reads the scoped style");
        assertEquals(BorderedButtonStyle.INSTANCE, seen.get(1), "Environment.value(BUTTON_STYLE) returns it");

        // Without any scope: buttonStyle() falls back, Environment.value yields null.
        seen.clear();
        new Emitter(sink()).mount(probe, Environment.DEFAULT);
        assertEquals(PlainButtonStyle.INSTANCE, seen.get(0), "unset → the PlainButtonStyle default");
        assertEquals(null, seen.get(1), "unset → Environment.value yields null");
    }

    @Test
    void buttonStyleScopedAboveAStructuralSlotSurvivesReRender() {
        // A `.buttonStyle(...)` scope lives in the generic environment, so a structural
        // slot re-rendering its destination outside the container's render path (a
        // navigation destination swap) still sees it — a destination reading
        // `env.buttonStyle()` gets the scoped style, not the plain default.
        java.util.List<ButtonStyle> seen = new java.util.ArrayList<>();
        Router router = Navigation.navigator()
                .route("/", p -> probeButton(seen, "One"))
                .route("/two", p -> probeButton(seen, "Two"))
                .build();
        new Emitter(sink()).mount(
                NavigationContainer.of(router).modifier(ButtonStyleMod.of(BorderedButtonStyle.INSTANCE)),
                Environment.DEFAULT);

        seen.clear(); // mount renders the initial destination (once or twice); only the swap matters
        router.navigate("/two"); // structural swap re-renders the destination via reconcileSlot
        assertEquals(1, seen.size());
        assertEquals(BorderedButtonStyle.INSTANCE, seen.get(0),
                "a destination re-rendered via the slot keeps the scoped style");
    }

    /** A destination that records the button style it renders with. */
    private static View probeButton(java.util.List<ButtonStyle> seen, String label) {
        return new View() {
            @Override
            public com.pathland.view.emit.PathlandNode render(Environment env) {
                seen.add(env.buttonStyle());
                return Button.of(label, () -> { }).render(env);
            }
        };
    }

    @Test
    void boundRouterReRoutesGuardAwareOnExternalPathWrites() {
        // The router is bound to an external active-path signal (Platform.ACTIVE_PATH).
        // External writes are routed through guards — never bypassed.
        com.pathland.view.signal.WritableSignal<String> activePath =
                com.pathland.view.signal.Signals.signal("/");
        Router router = Navigation.navigator()
                .route("/", Text.of("Home"))
                .route("/users", Text.of("Users"))
                .route("/admin", p -> false, "/", Text.of("Admin")) // guard always redirects home
                .build(activePath);

        assertEquals("/", router.path(), "initial signal value drives the first route");

        activePath.set("/users"); // external (host) write
        assertEquals("/users", router.path(), "external write re-routes the router");

        activePath.set("/admin"); // guarded path
        assertEquals("/", router.path(), "a guarded external path redirects (guards not bypassed)");
        assertEquals("/", activePath.get(), "the redirect is mirrored back into the signal");
    }

    @Test
    void boundRouterMirrorsItsOwnNavigationIntoTheSignal() {
        com.pathland.view.signal.WritableSignal<String> activePath =
                com.pathland.view.signal.Signals.signal("/");
        Router router = Navigation.navigator()
                .route("/", Text.of("Home"))
                .route("/users", Text.of("Users"))
                .route("/users/42", Text.of("User 42"))
                .build(activePath);

        router.push("/users"); // app-initiated navigation
        assertEquals("/users", activePath.get(), "push mirrors into the active-path signal");
        assertEquals(2, router.depth());

        router.navigate("/users/42");
        assertEquals("/users/42", activePath.get(), "navigate mirrors into the signal");
        assertEquals(2, router.depth(), "navigate does not grow the back-stack");
    }

    @Test
    void onPathChangeFiresOnActivePathChanges() {
        com.pathland.view.signal.WritableSignal<String> activePath =
                com.pathland.view.signal.Signals.signal("/");
        java.util.List<String> seen = new java.util.ArrayList<>();
        View root = Text.of("x").onPathChange(seen::add);

        new Emitter(sink()).mount(
                root.environment(com.pathland.view.Platform.ACTIVE_PATH, activePath),
                Environment.DEFAULT);
        assertEquals(java.util.List.of("/"), seen, "fires initially with the current path");

        activePath.set("/kitchen");
        assertEquals(java.util.List.of("/", "/kitchen"), seen, "fires on each change");
    }
}