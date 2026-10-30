package dev.pathland.view.emit;

import dev.pathland.view.Environment;
import dev.pathland.view.EnvironmentValues;
import dev.pathland.view.View;
import dev.pathland.view.signal.EffectRef;
import dev.pathland.view.signal.Tracked;

import java.util.ArrayList;
import java.util.List;

/**
 * One component invocation in the emitter's render tree. A slot owns a tracked
 * render computation (the view's {@code body()}/{@code renderNode()}), the retained
 * node it produced, and its child slots. A signal read during the render subscribes
 * the slot; a later write re-runs the render and reconciles only this slot's subtree.
 *
 * <p>The slot tree mirrors the component tree, not the node tree: a composite's
 * root-chain delegation stays in one slot, and each child view rendered by a primitive
 * is a child slot. A primitive's node may contain extra nodes that belong to the slot;
 * nodes owned by a child slot carry that child as {@link PathlandNode#slot}.
 */
final class ViewSlot {

    /** The view whose render this slot tracks. */
    final View view;

    /** The environment instance passed to the render (state, etc.). */
    final Environment env;

    /** The environment scope captured at render time; re-applied on every re-run. */
    final EnvironmentValues envScope;

    /** The component parent slot (null for the root slot). */
    final ViewSlot parent;

    /** Committed child slots (the current retained render). */
    final List<ViewSlot> children = new ArrayList<>();

    /** Child slots collected during the render in progress (null outside a render). */
    List<ViewSlot> renderingChildren;

    /** The retained node this slot produced (for a composite, its root chain's node). */
    PathlandNode node;

    /** The retained node's parent node + index (null/-1 for the root slot). */
    PathlandNode nodeParent;
    int nodeIndex = -1;

    /** The synchronous tracked render computation. */
    Tracked<PathlandNode> tracked;

    /** Node-level binding effects owned by this slot (not its child slots). */
    final List<EffectRef> bindings = new ArrayList<>();

    /**
     * The node this slot occupied in the previous render of its parent (by child
     * position). Used to carry retained state (a nested {@code SIZE_THAT_FITS}
     * selection) across a parent re-render.
     */
    PathlandNode oldNodeHint;

    ViewSlot(View view, Environment env, EnvironmentValues envScope, ViewSlot parent) {
        this.view = view;
        this.env = env;
        this.envScope = envScope;
        this.parent = parent;
    }

    /** Start collecting the child slots of a new render pass. */
    void beginRenderChildren() {
        renderingChildren = new ArrayList<>();
    }

    /** Add a child slot created during the current render pass. */
    void addRenderingChild(ViewSlot child) {
        if (renderingChildren != null) {
            renderingChildren.add(child);
        } else {
            children.add(child);
        }
    }

    /** Commit the children of a first render. */
    void finishFirstRender() {
        children.clear();
        if (renderingChildren != null) {
            children.addAll(renderingChildren);
            renderingChildren = null;
        }
    }

    /** Take the children of a re-render (the caller swaps them with the old list). */
    List<ViewSlot> takeRenderedChildren() {
        List<ViewSlot> result = renderingChildren == null ? List.of() : renderingChildren;
        renderingChildren = null;
        return result;
    }
}
