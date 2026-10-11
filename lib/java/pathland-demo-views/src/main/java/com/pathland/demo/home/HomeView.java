package com.pathland.demo.home;

import com.pathland.view.*;
import com.pathland.view.router.NavigationIntent;

/**
 * The Home content area of the {@code SplitNavDemo}: a titled welcome pane with a short
 * description and a button that navigates declaratively (spec DSL.md §4.5 — any
 * component can change the route). The {@code .navigate} intent resolves to the nearest
 * enclosing router, so no router is threaded by hand. Self-contained and instantiated
 * inline in the route table as {@code new HomeView()}.
 */
public final class HomeView implements View {

    @Override
    public View body() {
        return VStack
                // The destination content region → `<main>`.
                .modifiers(
                        AccessibilityRole.with(Roles.MAIN),
                        Padding.with(24))
                .with(v -> v.alignment(HorizontalAlignment.LEADING).spacing(24))
                .children(
                        VStack
                                .with(v -> v.alignment(HorizontalAlignment.LEADING).spacing(2))
                                .children(
                                        // The destination title is a heading → `<h2>`.
                                        Text.with("Home").modifiers(
                                                FontSize.with(24), FontWeight.BOLD, AccessibilityRole.with(Roles.HEADER)),
                                        Text.with("A master-detail (split) navigation demo: the menu on the left "
                                                + "drives the content area on the right.")),
                        // Declarative route-change: resolves to the nearest enclosing router.
                        Button.with(b -> b.title("Open kitchen sink").action(() -> {}))
                                .modifiers(NavigationIntent.navigate("/kitchen")));
    }
}