package com.pathland.view.router;

import com.pathland.view.Environment;
import com.pathland.view.View;
import com.pathland.view.emit.PathlandNode;

/**
 * A view wrapped by {@code .navigate/.push/.replace}: records the declared
 * navigation intent on the built node (spec DSL.md §4.5 "any component can change
 * the route"). The intent — a target path plus a {@link NavOp} — is resolved by the
 * emitter to the **nearest enclosing** {@link Router} and surfaced on
 * {@code RenderResult.navigateActions} for the host to route a tap. No router is
 * threaded by hand.
 */
final class NavigationIntentView implements View {

    private final View content;
    private final String to;
    private final NavOp op;

    NavigationIntentView(View content, String to, NavOp op) {
        this.content = content;
        this.to = to;
        this.op = op;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = content.render(env);
        node.navigateTo = to;
        node.navOp = op;
        return node;
    }
}