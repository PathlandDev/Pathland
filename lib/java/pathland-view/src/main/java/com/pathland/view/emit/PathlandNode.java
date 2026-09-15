package com.pathland.view.emit;

import com.pathland.view.View;
import com.pathland.view.router.NavOp;
import com.pathland.view.signal.Signal;
import com.pathland.view.transport.Event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A node in the retained view tree — the app's canonical tree, produced by
 * {@code View.render(Environment)}. The {@link Emitter} assigns stable ids,
 * walks this tree to emit the structural opcodes, and registers fine-grained
 * binding effects for reactive text/properties.
 *
 * <p>Property values are stored typed:
 * {@link Float} (f32), {@link Integer} (raw u32/u8 bits), {@link com.pathland.view.Color},
 * or {@link String} (STRING-typed properties). The emitter derives the wire value
 * type from the property id.
 */
public final class PathlandNode {

    /** Stable node id, assigned by {@link Emitter} (pre-order from 1). */
    public int id;

    /** Protocol component type id (see {@code com.pathland.view.Components}). */
    public int component;

    /** Static text content (text node value / button label). Null when bound. */
    public String text;

    /** property id → typed value. */
    public final Map<Integer, Object> properties = new LinkedHashMap<>();

    /** property id → reactive signal (re-emits only that property on change). */
    public final Map<Integer, Signal<?>> propertyBindings = new LinkedHashMap<>();

    /** Reactive text binding (re-emits only {@code SET_TEXT} on change). */
    public Signal<String> textBinding;

    /** Reactive date binding for a {@code DATE_PICKER} (days since epoch; re-emits {@code SET_DATE}). */
    public Signal<Integer> dateBinding;

    /** Current date value: days since epoch. */
    public int days;

    /** Current date value: millis of day (0..86,400,000). */
    public int millisOfDay;

    /** Ordered children. */
    public final List<PathlandNode> children = new ArrayList<>();

    /** App-side tap callbacks (resolved by node id after the emitter assigns ids). */
    public final List<Runnable> tapActions = new ArrayList<>();

    /**
     * Declared navigation intent (spec DSL.md §4.5 "any component can change the
     * route"): a non-null {@code navigateTo} marks this node as a route-changer —
     * the emitter resolves it to the **nearest enclosing** {@code Router} (the
     * {@code NavigationContainer} this node lives under) and registers it in
     * {@code RenderResult.navigateActions}. No router is threaded by hand.
     */
    public String navigateTo;
    public NavOp navOp = NavOp.NAVIGATE;

    /** Writable text-input sink (text fields); the emitter routes TEXT_CHANGED into it. */
    public Consumer<String> textInput;

    /** Writable value-input sink (sliders/switches); the emitter routes VALUE_CHANGED into it. */
    public Consumer<Float> valueInput;

    /** Writable date-input sink (date pickers); the emitter routes DATE_CHANGED into it. */
    public DateInput dateInput;

    /**
     * Structural slot: when non-null this node's single child subtree is selected by
     * a signal and **reconciled** by the emitter on selector change (spec DSL.md §3.4).
     * The emitter re-evaluates the supplier (which reads the selector signal) and
     * diffs the resulting subtree against the retained one, emitting only `TREE`
     * deltas. All other reactivity stays fine-grained.
     */
    public Supplier<View> structuralContent;

    /**
     * Structural slot reactive STRING property: re-evaluated and re-emitted by the
     * structural effect <em>within the same reconcile frame</em> as the subtree swap
     * (e.g. the router's {@code ROUTE} property). Keeps a navigation to one frame.
     */
    public Integer structuralStringProperty;
    public Supplier<String> structuralStringValue;

    /**
     * Structural slot reactive U32 property: re-evaluated and re-emitted by the
     * structural effect <em>within the same reconcile frame</em> as the subtree
     * swap (e.g. the router's {@code NAV_DEPTH} property), so native navigation
     * adapters reconcile their page stack by depth.
     */
    public Integer structuralU32Property;
    public java.util.function.Supplier<Integer> structuralU32Value;

    /**
     * Global navigation sink: the host forwards raw {@code NAVIGATE} events here
     * (a {@code NavigationContainer} sets this to its router's handler). Unlike the
     * node-keyed input sinks, this is global — a {@code NAVIGATE} event has no target.
     */
    public Consumer<Event> navigateHandler;

    /**
     * The {@code Router} this node's subtree navigates against (a
     * {@code NavigationContainer} sets this to its router). The emitter tracks the
     * **nearest enclosing** one down the retained tree to resolve declarative
     * {@code navigateTo} intents (spec DSL.md §4.5).
     */
    public com.pathland.view.router.Router router;

    /**
     * The environment scope that must apply when this node's children render. A
     * structural slot (a {@code NavigationContainer} or {@code Conditional.when}
     * slot) captures the scope its destination subtree should see — the incoming
     * scope plus any slot-provided bindings (e.g. the container's router) — so the
     * emitter can re-apply it when the slot re-renders its destination in
     * {@code Emitter.reconcileSlot} (destinations read values like
     * {@code Navigation.ROUTER} on every re-render, not just mount).
     */
    public com.pathland.view.EnvironmentValues environmentForChildren;

    /**
     * Listeners for the active platform path (spec DSL.md §4.5 — {@code onPathChange}):
     * the emitter registers a node-level effect watching {@link #activePath} that fires
     * them on change, destroyed when this subtree is replaced.
     */
    public final java.util.List<java.util.function.Consumer<String>> pathChangeListeners = new ArrayList<>();

    /** The active-path signal ({@code Platform.ACTIVE_PATH}) captured at render; null when not provided. */
    public com.pathland.view.signal.Signal<String> activePath;

    public PathlandNode(int component) {
        this.component = component;
    }

    /** Whether this node carries reactive (signal-backed) text. */
    public boolean hasReactiveText() {
        return textBinding != null;
    }

    /** Current text value: the bound signal's value when reactive, else the static text. */
    public String textValue() {
        return textBinding != null ? textBinding.get() : text;
    }
}