package dev.pathland.view.dsl

import dev.pathland.view.core.View
import dev.pathland.view.core.router.Chrome
import dev.pathland.view.core.router.Navigation
import dev.pathland.view.core.router.NavigationIntent
import dev.pathland.view.NavigationLink as JNavigationLink
import dev.pathland.view.core.router.Router
import dev.pathland.view.core.signal.Signal

/**
 * Build a seeded router:
 *
 * ```
 * val router = navigator("/") {
 *     route("/", HomeView())
 *     route("/users/:id") { params -> UserView(params.intValue("id")) }
 *     fallback(NotFoundView())
 * }
 * ```
 */
fun navigator(initialPath: String = "/", routes: Navigation.Builder.() -> Unit): Router =
    Navigation.navigator(initialPath).apply(routes).build()

/** Build a router bound to the host-provided active-path signal (deep links/popstate). */
fun navigator(activePath: Signal<String>, routes: Navigation.Builder.() -> Unit): Router =
    Navigation.navigator().apply(routes).build(activePath)

/** The navigation slot (SwiftUI `NavigationStack` shape). */
fun NavigationStack(
    router: Router,
    chrome: Chrome = Chrome.PLATFORM_DEFAULT,
): View = Navigation.container(router, chrome).registerChild()

/** Alias of [NavigationStack] using the protocol's component name. */
fun NavigationContainer(
    router: Router,
    chrome: Chrome = Chrome.PLATFORM_DEFAULT,
): View = NavigationStack(router, chrome)

/** A text-labelled navigation link. */
fun NavigationLink(
    label: String,
    to: String,
    router: Router? = null,
): View = JNavigationLink.make { c ->
    c.label(label).to(to)
    if (router != null) c.router(router)
}.registerChild()

/** A navigation link with a custom label subtree. */
fun NavigationLink(
    to: String,
    router: Router? = null,
    content: () -> Unit,
): View {
    val children = collectChildren(content)
    val label = when (children.size) {
        0 -> dev.pathland.view.Text.make("")
        1 -> children[0]
        else -> dev.pathland.view.Group.children(*children.toTypedArray())
    }
    return JNavigationLink.make { c ->
        c.label(label).to(to)
        if (router != null) c.router(router)
    }.registerChild()
}

/** A reactive "is this the active route" signal. */
fun isActive(router: Router, path: String): Signal<Boolean> = Navigation.isActive(router, path)

/** Declare a route-change intent on any view; the emitter resolves the nearest router. */
fun View.navigate(to: String): View = modifiers(NavigationIntent.navigate(to))

/** Push onto the nearest router's back-stack. */
fun View.push(to: String): View = modifiers(NavigationIntent.push(to))

/** Replace the current route without touching the back-stack. */
fun View.replace(to: String): View = modifiers(NavigationIntent.replace(to))
