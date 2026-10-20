package dev.pathland.view.router;

/**
 * The navigation operation a declarative route-changing component requests
 * (spec DSL.md §4.5 "any component can change the route"): the intent is recorded
 * on the node and resolved to the nearest enclosing {@link Router} by the emitter.
 *
 * <p>{@link #NAVIGATE} sets the route without touching the back-stack (a direct
 * selection, e.g. a sidebar menu), {@link #PUSH} pushes the current route onto the
 * back-stack first (a drill-down), {@link #REPLACE} swaps the current route without
 * a back-stack entry (a guard redirect).
 */
public enum NavOp {

    NAVIGATE,
    PUSH,
    REPLACE;

    /** Apply {@code op} to a router: {@code navigate}/{@code push}/{@code replace}. */
    public void applyTo(Router router, String path) {
        switch (this) {
            case PUSH -> router.push(path);
            case REPLACE -> router.replace(path);
            case NAVIGATE -> router.navigate(path);
        }
    }
}