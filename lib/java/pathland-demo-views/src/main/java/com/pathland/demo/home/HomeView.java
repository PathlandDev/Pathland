package com.pathland.demo.home;

import com.pathland.view.*;

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
        return VStack.of(HorizontalAlignment.LEADING, 24,
                VStack.of(HorizontalAlignment.LEADING, 2,
                    // The destination title is a heading → `<h2>`.
                    Text.of("Home").with(FontSize.of(24), FontWeightMod.of(FontWeight.BOLD))
                            .with(AccessibilityRole.of(Roles.HEADER)),
                    Text.of("A master-detail (split) navigation demo: the menu on the left "
                            + "drives the content area on the right.")
                ),
                // Declarative route-change: resolves to the nearest enclosing router.
                Button.of("Open kitchen sink", () -> {}).navigate("/kitchen")
        )
        // The destination content region → `<main>`.
        .with(AccessibilityRole.of(Roles.MAIN))
        .with(Padding.of(24));
    }
}