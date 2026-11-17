package dev.pathland.view.core.router

import dev.pathland.view.core.View

/**
 * Creates the destination view for a matched route.
 *
 * The handler receives the captured path/query [Params] and may return `null`
 * when it cannot build the destination (the caller renders nothing).
 */
fun interface RouteHandler {
    fun destination(params: Params): View?
}
