package com.pathland.view.router;

import com.pathland.view.View;
import com.pathland.view.ViewModifier;

/**
 * The navigation action modifier (spec DSL.md §4.5 "any component can change the
 * route"): marks a view as a route-changer, applied with
 * {@code .modifiers(NavigationIntent.navigate/push/replace(path))}. The intent is
 * resolved to the nearest enclosing {@link Router} by the emitter, so no router is
 * threaded by hand:
 *
 * <pre>{@code
 * Button.with(b -> b.title("Go to kitchen").action(...))
 *       .modifiers(NavigationIntent.navigate("/kitchen"));   // direct selection
 * Button.with(b -> b.title("Open item").action(...))
 *       .modifiers(NavigationIntent.push("/item/1"));        // drill-down (back-stack)
 * Button.with(b -> b.title("Swap").action(...))
 *       .modifiers(NavigationIntent.replace("/settings"));   // guard redirect / replace
 * }</pre>
 */
public final class NavigationIntent implements ViewModifier {

    private final String to;
    private final NavOp op;

    private NavigationIntent(String to, NavOp op) {
        this.to = to;
        this.op = op;
    }

    /** A {@link NavOp#NAVIGATE} route-change to {@code to}. */
    public static NavigationIntent navigate(String to) {
        return new NavigationIntent(to, NavOp.NAVIGATE);
    }

    /** A {@link NavOp#PUSH} route-change to {@code to} (drill-down). */
    public static NavigationIntent push(String to) {
        return new NavigationIntent(to, NavOp.PUSH);
    }

    /** A {@link NavOp#REPLACE} route-change to {@code to}. */
    public static NavigationIntent replace(String to) {
        return new NavigationIntent(to, NavOp.REPLACE);
    }

    @Override
    public View body(View content) {
        return new NavigationIntentView(content, to, op);
    }
}