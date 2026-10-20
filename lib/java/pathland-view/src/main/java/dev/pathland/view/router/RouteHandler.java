package dev.pathland.view.router;

import dev.pathland.view.View;

/**
 * Builds the {@link View} for a matched route (spec DSL.md §4.5). The {@link Params}
 * hold the path parameters captured from the pattern ({@code /users/:id} →
 * {@code {id: "42"}}), with typed access via {@code params.intValue("id")} etc.
 * Factories are lazy: the client only ever receives the chosen destination's deltas.
 */
@FunctionalInterface
public interface RouteHandler {

    /** The destination view for a matched route, given its captured path params. */
    View destination(Params params);
}