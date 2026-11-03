package dev.pathland.view.core.router

import dev.pathland.view.core.View

/** Builds the View for a matched route. */
fun interface RouteHandler {
    fun destination(params: Params): View?
}
