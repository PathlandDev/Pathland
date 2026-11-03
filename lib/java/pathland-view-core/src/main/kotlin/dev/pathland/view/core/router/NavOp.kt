package dev.pathland.view.core.router

/** The navigation operation a declarative route-changing component requests. */
enum class NavOp {
    NAVIGATE,
    PUSH,
    REPLACE;

    /** Apply this operation to [router]. */
    fun applyTo(router: Router, path: String) {
        when (this) {
            PUSH -> router.push(path)
            REPLACE -> router.replace(path)
            NAVIGATE -> router.navigate(path)
        }
    }
}
