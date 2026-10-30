package dev.pathland.view.emit;

import dev.pathland.view.Color;
import dev.pathland.view.Commands;
import dev.pathland.view.TokenPath;
import dev.pathland.view.Components;
import dev.pathland.view.Environment;
import dev.pathland.view.EnvironmentValues;
import dev.pathland.view.MediaController;
import dev.pathland.view.Properties;
import dev.pathland.view.WindowMetadata;
import dev.pathland.view.ThemeData;
import dev.pathland.view.ThemeMode;
import dev.pathland.view.ValueTypes;
import dev.pathland.view.View;
import dev.pathland.view.signal.ConstantSignal;
import dev.pathland.view.signal.EffectRef;
import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;
import dev.pathland.view.signal.Tracked;
import dev.pathland.view.transport.Event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The component-slot emitter. Mounts a {@link View} tree as a tree of tracked
 * {@link ViewSlot}s: each child view's {@code body()}/{@code renderNode()} runs inside
 * its own tracked computation, so a signal read there re-renders only that component
 * and its subtree is diffed against the retained nodes. Node-level binding effects
 * still make a bound text/property change emit exactly one {@code SET_TEXT} /
 * {@code SET_PROPERTY}.
 *
 * <p>The wire is always a diff: only actual {@code TREE}/{@code PARAMETER} changes are
 * emitted, and an identical recompute emits zero opcodes. The retained tree is the
 * source of truth; the fresh render is a throwaway description.
 */
public final class Emitter {

    private final OpcodeSink sink;
    private ThemeData theme;
    /** The root-declared window/surface metadata (null when absent). */
    private WindowMetadata windowMetadata;
    /** Last window-metadata value written per field (dedupe / clear detection). */
    private final Map<Integer, String> lastWindowMetadata = new LinkedHashMap<>();
    private final Map<Integer, Runnable> tapActions = new LinkedHashMap<>();
    private final Map<Integer, Consumer<String>> textInputs = new LinkedHashMap<>();
    private final Map<Integer, Consumer<Float>> valueInputs = new LinkedHashMap<>();
    private final Map<Integer, Consumer<Boolean>> editingInputs = new LinkedHashMap<>();
    private final Map<Integer, DateInput> dateInputs = new LinkedHashMap<>();
    private final Map<Integer, MediaInput> mediaInputs = new LinkedHashMap<>();
    private final Map<Integer, Consumer<Integer>> fitInputs = new LinkedHashMap<>();
    private final Map<Integer, Runnable> navigateActions = new LinkedHashMap<>();
    private final Map<Integer, dev.pathland.view.ProxyConfig> proxyViews = new LinkedHashMap<>();
    /** Materialized `MEDIA_CONTROLLER` nodes by controller identity (spec/MEDIA.md):
     *  one non-visual node per referenced controller, keyed by the controller id. */
    private final Map<MediaController, PathlandNode> mediaControllers = new IdentityHashMap<>();
    /** The last-emitted controller node id per controller — so a re-render that keeps
     *  the same id does not re-emit a redundant `MEDIA_CONTROLLER_ID` (an identical
     *  recompute must stay zero-op). */
    private final Map<MediaController, Integer> mediaControllerIds = new IdentityHashMap<>();
    private final List<EffectRef> bindings = new ArrayList<>();
    private final Map<Integer, List<EffectRef>> nodeBindings = new LinkedHashMap<>();
    private Consumer<Event> navigateHandler;
    private int nextId = 1;
    private int rootId;
    private boolean mounted;
    private PathlandNode tree;
    private Environment env;

    /** The root component slot (the whole app). */
    private ViewSlot rootSlot;

    /** The stack of component slots currently rendering (parent = peek()). */
    private final Deque<ViewSlot> renderStack = new ArrayDeque<>();

    /** Whether the next full emission (mount/resync) renders `SIZE_THAT_FITS`
     *  slots with ALL candidates as children (the web SSR container-query first
     *  paint, issue #141). Set by the SSR session before mount; internal only —
     *  the authored DSL and the live reconcile path are unchanged. */
    private boolean snapshotEmission;

    /** Whether fit slots should still render their full candidate set (until the
     *  host's first FIT_CHANGED). */
    private boolean snapshotActive;

    /** Fired after a structural reconcile (see {@link #onStructureReconciled}). */
    private Runnable structureReconciled;

    /** The emitter currently driving a render on this thread (null outside a mount or a
     *  tracked slot re-run). {@link View#render(Environment)} consults this to create a
     *  component slot instead of materializing directly. */
    private static final ThreadLocal<Emitter> CURRENT = new ThreadLocal<>();

    /** The emitter driving the current render on this thread, or null. */
    public static Emitter current() {
        return CURRENT.get();
    }

    /**
     * Render one component slot (see {@link View#render(Environment)}). Creates a
     * tracked computation for the view, runs it synchronously, and returns the
     * retained node. A later signal write re-runs the computation and reconciles only
     * this slot's subtree.
     */
    public PathlandNode renderView(View view, Environment env) {
        ViewSlot parent = renderStack.peek();
        ViewSlot slot = new ViewSlot(view, env, Environment.current(), parent);
        if (parent != null) {
            slot.oldNodeHint = oldChildHint(parent, parent.renderingChildren == null ? 0 : parent.renderingChildren.size());
            parent.addRenderingChild(slot);
        }
        Tracked<PathlandNode> tracked = Signals.tracked(
                () -> renderSlot(slot),
                fresh -> reconcileSlot(slot, fresh));
        slot.tracked = tracked;
        bindings.add(tracked);
        PathlandNode node = tracked.initial();
        if (node == null) {
            node = new PathlandNode(Components.VSTACK);
        }
        if (node.slot != null && node.slot != slot
                && slot.renderingChildren != null && slot.renderingChildren.contains(node.slot)) {
            throw new IllegalStateException(
                    "View " + view.getClass().getName() + " returned a node owned by a child slot; "
                            + "use renderNode(env) (not render(env)) for root-chain delegation");
        }
        slot.node = node;
        node.slot = slot;
        slot.finishFirstRender();
        // The retained-node hint is only for the slot's first render (carrying state
        // across a parent re-render); later re-runs use the slot's own signals.
        slot.oldNodeHint = null;
        if (parent == null) {
            rootSlot = slot;
        }
        return node;
    }

    /** Run one slot render with the slot current, its environment scope applied, and
     *  the emitter installed for nested {@link View#render} calls. */
    private PathlandNode renderSlot(ViewSlot slot) {
        Emitter previous = CURRENT.get();
        CURRENT.set(this);
        renderStack.push(slot);
        EnvironmentValues previousEnv = Environment.current();
        Environment.within(slot.envScope);
        boolean previousSnapshot = PathlandNode.SNAPSHOT_EMISSION.get();
        PathlandNode.SNAPSHOT_EMISSION.set(snapshotActive);
        try {
            slot.beginRenderChildren();
            return slot.view.renderNode(slot.env);
        } finally {
            PathlandNode.SNAPSHOT_EMISSION.set(previousSnapshot);
            Environment.restore(previousEnv);
            renderStack.pop();
            CURRENT.set(previous);
        }
    }

    /** The old retained node a child slot would occupy at {@code index}, used to carry
     *  retained state (a nested fit selection) across a parent re-render. */
    private static PathlandNode oldChildHint(ViewSlot parent, int index) {
        PathlandNode oldParent = parent.node != null ? parent.node : parent.oldNodeHint;
        if (oldParent == null || index < 0 || index >= oldParent.children.size()) {
            return null;
        }
        return oldParent.children.get(index);
    }

    /** The fit selection the current slot should inherit from the node it replaced, or
     *  null when this is not a re-rendered fit slot. */
    public Integer currentSlotOldFitSelection() {
        ViewSlot slot = renderStack.peek();
        if (slot == null || slot.oldNodeHint == null
                || slot.oldNodeHint.component != Components.SIZE_THAT_FITS) {
            return null;
        }
        return slot.oldNodeHint.fitSelection;
    }

    public Emitter(OpcodeSink sink) {
        this(sink, null);
    }

    /**
     * Create an emitter with a global theme ({@link ThemeData}: a single
     * {@link Theme} or an {@link AdaptiveTheme} light+dark pair). The theme's
     * {@code PARAMETER::SET_DESIGN_TOKEN} overrides are emitted at the start of the
     * **mount** and **renderFull** (resync) frames, so the SSR document and the
     * first client frame both carry them. Overrides are renderer-global and
     * never re-emit nodes; deltas are unaffected.
     */
    public Emitter(OpcodeSink sink, ThemeData theme) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.theme = theme;
    }

    /**
     * Enable full-snapshot fit-slot expansion (see {@link #snapshotEmission}).
     * Called by the server-core SSR session before mount. Internal plumbing
     * between {@code pathland-view} and {@code pathland-server-core} — not part
     * of the DSL authoring surface.
     */
    public void snapshotEmission(boolean on) {
        this.snapshotEmission = on;
    }

    /**
     * Register a callback fired after a **structural reconcile** (a slot's child
     * subtree was added/removed, spec DSL.md §3.4) — after the input routing maps
     * reflect the new subtree, so a host reading them sees the current state. The
     * server session uses it to reconcile proxy sessions against the live
     * {@code RenderResult.proxyViews()} map: a {@code PROXY_VIEW} node that left the
     * retained tree (e.g. a {@code SizeThatFits} slot dropping the candidate that
     * held it) closes its origin connection, and a new one opens a fresh connection.
     * Internal plumbing between {@code pathland-view} and {@code pathland-server-core}
     * — not part of the DSL authoring surface.
     */
    public void onStructureReconciled(Runnable callback) {
        this.structureReconciled = callback;
    }

    private void fireStructureReconciled() {
        Runnable callback = structureReconciled;
        if (callback != null) {
            callback.run();
        }
    }

    // ── Component slots ──────────────────────────────────────────────────────

    /** Link each retained node to its parent/index and update the owning slot's
     *  position. Called after mount and after every slot reconcile. */
    private void linkSlots(PathlandNode root) {
        linkSlots(root, null, -1);
    }

    private void linkSlots(PathlandNode node, PathlandNode parent, int index) {
        node.parent = parent;
        node.index = index;
        if (node.slot != null) {
            node.slot.nodeParent = parent;
            node.slot.nodeIndex = index;
        }
        for (int i = 0; i < node.children.size(); i++) {
            linkSlots(node.children.get(i), node, i);
        }
    }

    /**
     * Reconcile a slot's fresh render against its retained node. Only this slot's
     * subtree is diffed; unchanged nodes emit zero opcodes.
     */
    private void reconcileSlot(ViewSlot slot, PathlandNode fresh) {
        List<ViewSlot> oldChildren = slot.children;
        List<ViewSlot> newChildren = slot.takeRenderedChildren();
        if (fresh == null) {
            fresh = new PathlandNode(Components.VSTACK);
        }
        fresh.slot = slot;
        // Media reads must not subscribe the component to playback signals.
        PathlandNode freshNode = fresh;
        Signals.untracked(() -> {
            materializeControllers(freshNode);
            return null;
        });

        PathlandNode old = slot.node;
        destroySlotBindings(slot);
        forgetSlotInputs(slot);

        // SSR full-snapshot -> live downgrade (issue #141): the slot carried every
        // candidate; now it carries one. Keep the SELECTED old candidate node as-is
        // and drop the others instead of positionally morphing candidate 0 into the
        // winner (the winner must not move into the loser's SSR wrapper, and keeping
        // it makes the downgrade zero-churn).
        if (old != null && old.component == Components.SIZE_THAT_FITS
                && fresh.component == Components.SIZE_THAT_FITS
                && old.children.size() > 1 && newChildren.size() == 1) {
            reconcileSnapshotFit(slot, old, fresh, oldChildren, newChildren);
            return;
        }

        for (ViewSlot child : oldChildren) {
            destroySlotTree(child);
        }

        List<Runnable> ops = new ArrayList<>();
        if (old != null && old.component == fresh.component) {
            fresh.id = old.id;
            reconcileNode(old, fresh, ops);
        } else {
            if (slot.nodeParent == null) {
                throw new IllegalStateException(
                        "the root view's component type must stay stable across reactive re-renders; "
                                + "wrap it in a stable container (e.g. View.of or VStack)");
            }
            int oldId = old.id;
            int parentId = slot.nodeParent.id;
            ops.add(() -> {
                sink.removeChild(parentId, oldId);
                sink.deleteNode(oldId);
            });
            fresh.id = freshIdFor(fresh);
            int newId = fresh.id;
            int component = fresh.component;
            ops.add(() -> sink.createNode(newId, component));
            emitSubtreeInto(ops, fresh);
            int index = slot.nodeIndex;
            ops.add(() -> sink.insertChild(parentId, newId, index));
        }

        if (!ops.isEmpty()) {
            sink.beginFrame();
            for (Runnable op : ops) {
                op.run();
            }
            sink.endFrame();
        }

        slot.node = fresh;
        slot.children.clear();
        slot.children.addAll(newChildren);
        if (slot.nodeParent != null) {
            slot.nodeParent.children.set(slot.nodeIndex, fresh);
        } else {
            this.tree = fresh;
        }
        linkSlots(fresh);
        registerSlotTree(slot);
        collectSlotTree(slot);
        refreshNavigateHandler();
        guardUniqueIds();
        fireStructureReconciled();
    }

    /** Snapshot N-candidate fit slot -> one selected candidate, retaining the selected
     *  old node (zero-churn downgrade, issue #141). */
    private void reconcileSnapshotFit(
            ViewSlot slot,
            PathlandNode old,
            PathlandNode fresh,
            List<ViewSlot> oldChildren,
            List<ViewSlot> newChildren) {
        int selected = Math.min(Math.max(fresh.fitSelection, 0), old.children.size() - 1);
        PathlandNode keep = old.children.get(selected);
        ViewSlot keepSlot = keep.slot;
        List<Runnable> dropOps = new ArrayList<>();
        for (PathlandNode child : old.children) {
            if (child != keep) {
                int parentId = old.id;
                int childId = child.id;
                dropOps.add(() -> {
                    sink.removeChild(parentId, childId);
                    sink.deleteNode(childId);
                });
            }
        }
        for (ViewSlot child : oldChildren) {
            if (child != keepSlot) {
                destroySlotTree(child);
            }
        }
        for (ViewSlot child : newChildren) {
            destroySlotTree(child);
        }
        old.children.clear();
        old.children.add(keep);
        fresh.children.clear();
        fresh.children.add(keep);
        slot.children.clear();
        if (keepSlot != null) {
            slot.children.add(keepSlot);
        }
        fresh.id = old.id;
        if (!dropOps.isEmpty()) {
            sink.beginFrame();
            for (Runnable op : dropOps) {
                op.run();
            }
            sink.endFrame();
        }
        List<Runnable> ops = new ArrayList<>();
        reconcileNode(old, fresh, ops);
        if (!ops.isEmpty()) {
            sink.beginFrame();
            for (Runnable op : ops) {
                op.run();
            }
            sink.endFrame();
        }
        slot.node = fresh;
        if (slot.nodeParent != null) {
            slot.nodeParent.children.set(slot.nodeIndex, fresh);
        } else {
            this.tree = fresh;
        }
        linkSlots(fresh);
        registerSlotBindings(slot);
        collectSlotInputs(slot, null);
        refreshNavigateHandler();
        guardUniqueIds();
        fireStructureReconciled();
    }

    /** Register node-level bindings for a slot and all of its child slots. */
    private void registerSlotTree(ViewSlot slot) {
        registerSlotBindings(slot);
        for (ViewSlot child : slot.children) {
            registerSlotTree(child);
        }
    }

    /** Register node-level bindings for the nodes this slot owns (not child slots). */
    private void registerSlotBindings(ViewSlot slot) {
        if (slot.node == null) {
            return;
        }
        walkOwned(slot.node, slot, node -> registerNodeBindings(slot, node));
    }

    /** Collect input routing for a slot and all of its child slots. */
    private void collectSlotTree(ViewSlot slot) {
        collectSlotInputs(slot, null);
    }

    private dev.pathland.view.router.Router collectSlotInputs(
            ViewSlot slot, dev.pathland.view.router.Router enclosing) {
        dev.pathland.view.router.Router scope = enclosing;
        if (slot.node != null) {
            scope = collectOwnedInputs(slot.node, slot, enclosing);
        }
        for (ViewSlot child : slot.children) {
            collectSlotInputs(child, scope);
        }
        return scope;
    }

    private dev.pathland.view.router.Router collectOwnedInputs(
            PathlandNode node, ViewSlot slot, dev.pathland.view.router.Router enclosing) {
        if (node.slot != null && node.slot != slot) {
            return enclosing;
        }
        dev.pathland.view.router.Router scope = node.router != null ? node.router : enclosing;
        collectInputsForNode(node, scope);
        for (PathlandNode child : node.children) {
            scope = collectOwnedInputs(child, slot, scope);
        }
        return scope;
    }

    private void collectInputsForNode(
            PathlandNode node, dev.pathland.view.router.Router enclosing) {
        for (Runnable action : node.tapActions) {
            tapActions.put(node.id, action);
        }
        if (node.textInput != null) {
            textInputs.put(node.id, node.textInput);
        }
        if (node.valueInput != null) {
            valueInputs.put(node.id, node.valueInput);
        }
        if (node.editingInput != null) {
            editingInputs.put(node.id, node.editingInput);
        }
        if (node.dateInput != null) {
            dateInputs.put(node.id, node.dateInput);
        }
        if (node.mediaInput != null) {
            mediaInputs.put(node.id, node.mediaInput);
        }
        if (node.fitInput != null) {
            Consumer<Integer> inner = node.fitInput;
            fitInputs.put(node.id, index -> {
                snapshotActive = false;
                inner.accept(index);
            });
        }
        if (node.proxyConfig != null) {
            proxyViews.put(node.id, node.proxyConfig);
        }
        if (node.navigateHandler != null) {
            navigateHandler = node.navigateHandler;
        }
        if (node.navigateTo != null && enclosing != null) {
            String to = node.navigateTo;
            dev.pathland.view.router.NavOp op = node.navOp;
            int id = node.id;
            dev.pathland.view.router.Router router = enclosing;
            navigateActions.put(id, () -> op.applyTo(router, to));
        }
    }

    private void refreshNavigateHandler() {
        navigateHandler = null;
        collectNavigateHandler(this.tree);
    }

    private void collectNavigateHandler(PathlandNode node) {
        if (node.navigateHandler != null) {
            navigateHandler = node.navigateHandler;
        }
        for (PathlandNode child : node.children) {
            collectNavigateHandler(child);
        }
    }

    /** Destroy a slot subtree (its tracked computation, bindings, and routing). */
    private void destroySlotTree(ViewSlot slot) {
        for (ViewSlot child : slot.children) {
            destroySlotTree(child);
        }
        destroySlotBindings(slot);
        forgetSlotInputs(slot);
        if (slot.tracked != null) {
            bindings.remove(slot.tracked);
            slot.tracked.destroy();
            slot.tracked = null;
        }
    }

    /** Destroy the node-level binding effects owned by this slot. */
    private void destroySlotBindings(ViewSlot slot) {
        for (EffectRef effect : slot.bindings) {
            effect.destroy();
            bindings.remove(effect);
        }
        slot.bindings.clear();
        if (slot.node != null) {
            walkOwned(slot.node, slot, node -> nodeBindings.remove(node.id));
        }
    }

    /** Remove input routing for the nodes this slot owns (child slots handle theirs). */
    private void forgetSlotInputs(ViewSlot slot) {
        if (slot.node == null) {
            return;
        }
        walkOwned(slot.node, slot, this::forgetInputsForNode);
    }

    private void forgetInputsForNode(PathlandNode node) {
        tapActions.remove(node.id);
        textInputs.remove(node.id);
        valueInputs.remove(node.id);
        editingInputs.remove(node.id);
        dateInputs.remove(node.id);
        mediaInputs.remove(node.id);
        fitInputs.remove(node.id);
        navigateActions.remove(node.id);
        proxyViews.remove(node.id);
    }

    /** Visit the nodes owned by {@code slot}: its own node, extra nodes it created,
     *  and not the subtrees owned by child slots. */
    private void walkOwned(PathlandNode node, ViewSlot slot, Consumer<PathlandNode> visit) {
        if (node.slot != null && node.slot != slot) {
            return;
        }
        visit.accept(node);
        for (PathlandNode child : node.children) {
            walkOwned(child, slot, visit);
        }
    }

    /** Mount the view tree: assign ids, emit the structural frame, register bindings. */
    public RenderResult mount(View root, Environment env) {
        if (mounted) {
            throw new IllegalStateException("Emitter already mounted; create a new Emitter per tree");
        }
        mounted = true;
        this.env = env;
        this.snapshotActive = snapshotEmission;

        Emitter previous = CURRENT.get();
        CURRENT.set(this);
        PathlandNode tree;
        try {
            tree = root.render(env);
        } finally {
            CURRENT.set(previous);
        }
        this.tree = tree;
        this.windowMetadata = env.windowMetadata();
        nextId = 1;
        assignIds(tree);
        linkSlots(tree);
        // Materialize one non-visual `MEDIA_CONTROLLER` node per referenced
        // MediaController (spec/MEDIA.md) and bind each `VIDEO` surface to it.
        // The controller nodes are prepended so they are emitted before any
        // surface references them (a forward reference would render blank).
        mediaControllers.clear();
        mediaControllerIds.clear();
        materializeControllers(tree);
        guardUniqueIds();
        tapActions.clear();
        textInputs.clear();
        valueInputs.clear();
        editingInputs.clear();
        dateInputs.clear();
        mediaInputs.clear();
        fitInputs.clear();
        navigateActions.clear();
        proxyViews.clear();
        navigateHandler = null;
        collectSlotTree(rootSlot);

        sink.beginFrame();
        emitTheme();
        emitWindowMetadata(false);
        emitNode(tree);
        sink.endFrame();

        registerSlotTree(rootSlot);
        registerWindowMetadataBindings();
        rootId = tree.id;
        return new RenderResult(
                rootId,
                Collections.unmodifiableMap(tapActions),
                Collections.unmodifiableMap(textInputs),
                Collections.unmodifiableMap(valueInputs),
                Collections.unmodifiableMap(editingInputs),
                Collections.unmodifiableMap(dateInputs),
                Collections.unmodifiableMap(mediaInputs),
                Collections.unmodifiableMap(fitInputs),
                Collections.unmodifiableMap(navigateActions),
                navigateHandler,
                Collections.unmodifiableMap(proxyViews));
    }

    /** The root node id (0 until mounted). */
    public int rootId() {
        return rootId;
    }

    /**
     * Re-emit the complete retained tree as one full-snapshot frame (TREE + PARAMETER).
     * Used for {@code META::RESYNC} (reconnect/gap recovery) and no-JS refresh. Does
     * not re-mount or re-wire state — it replays the current tree through the sink.
     */
    public void renderFull() {
        if (!mounted) {
            throw new IllegalStateException("Emitter not mounted");
        }
        guardUniqueIds();
        sink.beginFrame();
        emitTheme();
        // A reconnecting client may have missed a title/description change during
        // the gap, so a full snapshot always re-sends every metadata field.
        emitWindowMetadata(true);
        // The retained tree already carries every fit candidate when the session
        // mounted in snapshot mode; re-emit it as-is (a resync re-establishes the
        // container-query first paint for a reconnecting client). Before the first
        // FIT_CHANGED the tree IS the snapshot form (every candidate): the client
        // reuses its SSR gate (candidate wrappers + `@container` band rules) so the
        // width-correct candidate stays shown — a resync must NOT collapse it
        // (issue #141, spec/PRIMITIVES.md §SizeThatFits).
        emitNode(tree);
        sink.endFrame();
    }

    /** Emit the global theme overrides at the start of a full frame. */
    private void emitTheme() {
        if (theme != null) {
            theme.emitInto(sink);
        }
    }

    /**
     * Emit the window/surface metadata fields ({@code META::SET_WINDOW_METADATA}).
     * With {@code force} (a full snapshot) every field is re-sent; otherwise only
     * changed fields are written.
     */
    private void emitWindowMetadata(boolean force) {
        WindowMetadata metadata = this.windowMetadata;
        if (metadata == null) {
            return;
        }
        emitWindowField(Commands.WindowFields.TITLE, metadata.title(), force);
        emitWindowField(Commands.WindowFields.DESCRIPTION, metadata.description(), force);
        emitWindowField(Commands.WindowFields.LOCALE, metadata.locale(), force);
    }

    private void emitWindowField(int field, Signal<String> signal, boolean force) {
        String value;
        if (signal == null) {
            if (!lastWindowMetadata.containsKey(field) && !force) {
                return;
            }
            value = "";
        } else {
            value = signal.get();
            if (value == null) {
                value = "";
            }
        }
        if (force || !Objects.equals(lastWindowMetadata.get(field), value)) {
            sink.setWindowMetadata(field, value);
            lastWindowMetadata.put(field, value);
        }
    }

    /**
     * Bind each reactive window-metadata field to its signal. A change emits only
     * that field's {@code META::SET_WINDOW_METADATA}; an unchanged value emits
     * zero opcodes.
     */
    private void registerWindowMetadataBindings() {
        WindowMetadata metadata = this.windowMetadata;
        if (metadata == null) {
            return;
        }
        bindWindowField(Commands.WindowFields.TITLE, metadata.title());
        bindWindowField(Commands.WindowFields.DESCRIPTION, metadata.description());
        bindWindowField(Commands.WindowFields.LOCALE, metadata.locale());
    }

    private void bindWindowField(int field, Signal<String> signal) {
        if (signal == null || isConstant(signal)) {
            return;
        }
        EffectRef effect = Signals.effect(() -> {
            String value = signal.get();
            if (value == null) {
                value = "";
            }
            if (!Objects.equals(lastWindowMetadata.get(field), value)) {
                lastWindowMetadata.put(field, value);
                sink.beginFrame();
                sink.setWindowMetadata(field, value);
                sink.endFrame();
            }
        });
        bindings.add(effect);
    }

    /**
     * Replace the global theme at runtime: emits one frame with
     * {@code META::RESET_THEME} followed by the new theme's overrides
     * (spec/TOKENS.md §Runtime theming). The renderer re-resolves token
     * references; no node is re-emitted. A {@code null} theme clears all
     * overrides.
     */
    public void replaceTheme(ThemeData theme) {
        this.theme = theme;
        sink.beginFrame();
        sink.resetTheme();
        emitTheme();
        sink.endFrame();
    }

    /**
     * Force/release the effective color scheme at runtime
     * ({@code META::SET_COLOR_SCHEME}). Platform changes are ignored while a
     * forced mode is active.
     */
    public void setColorScheme(ThemeMode mode) {
        sink.beginFrame();
        sink.setColorScheme(mode.code());
        sink.endFrame();
    }

    /** Clear every application token override (renderer defaults apply). */
    public void resetTheme() {
        replaceTheme(null);
    }

    /** Stop all registered binding effects (e.g. when tearing down the tree). */
    public void destroy() {
        if (rootSlot != null) {
            destroySlotTree(rootSlot);
            rootSlot = null;
        }
        // Global effects (e.g. window metadata) are not slot-owned; destroy what
        // remains after the slot tree has released its own bindings.
        for (EffectRef effect : bindings) {
            effect.destroy();
        }
        bindings.clear();
        nodeBindings.clear();
    }

    private void assignIds(PathlandNode node) {
        node.id = nextFreeId();
        for (PathlandNode child : node.children) {
            assignIds(child);
        }
    }

    /**
     * The ids that occur more than once in {@code root}'s subtree, in pre-order.
     *
     * <p>This is the tree-scan half of the frame's **unique-node-id invariant**
     * (spec/OPCODE.md §Frame Lifecycle): a frame MUST NOT contain two nodes with the
     * same id, because every id-keyed consumer (the renderer's {@code id → element}
     * map, media reports, proxy remapping, …) treats the id as a single node. The
     * emitter calls this before every emission ({@link #guardUniqueIds()}) and tests
     * use it directly to prove a tree is clean.
     */
    public static List<Integer> duplicateIds(PathlandNode root) {
        Set<Integer> seen = new LinkedHashSet<>();
        Set<Integer> duplicates = new LinkedHashSet<>();
        collectDuplicateIds(root, seen, duplicates);
        return new ArrayList<>(duplicates);
    }

    private static void collectDuplicateIds(PathlandNode node, Set<Integer> seen, Set<Integer> duplicates) {
        if (!seen.add(node.id)) {
            duplicates.add(node.id);
        }
        for (PathlandNode child : node.children) {
            collectDuplicateIds(child, seen, duplicates);
        }
    }

    /**
     * Allocate the next node id, **skipping ids pinned for a media controller**
     * (spec/MEDIA.md). A controller's node id is its playback key and must stay
     * stable across re-renders; handing the same id to a freshly created node
     * would make the renderer lose (and restart) the playback.
     */
    private int nextFreeId() {
        while (mediaControllerIds.containsValue(nextId)) {
            nextId++;
        }
        return nextId++;
    }

    /**
     * The id a freshly emitted node takes. A materialized `MEDIA_CONTROLLER` keeps
     * its **pinned** id — the playback key must survive a subtree re-emission
     * (a content swap re-emits the whole destination, controller included), or the
     * renderer would tear down and restart playback (spec/MEDIA.md). Everything
     * else draws a fresh free id.
     */
    private int freshIdFor(PathlandNode node) {
        MediaController controller = node.mediaControllerRef;
        if (controller != null && node.component == Components.MEDIA_CONTROLLER) {
            Integer pinned = mediaControllerIds.get(controller);
            if (pinned != null) {
                return pinned;
            }
        }
        return nextFreeId();
    }

    /** Every node in a subtree, pre-order. */
    private static List<PathlandNode> flatten(PathlandNode node) {
        List<PathlandNode> out = new ArrayList<>();
        flattenInto(node, out);
        return out;
    }

    private static void flattenInto(PathlandNode node, List<PathlandNode> out) {
        out.add(node);
        for (PathlandNode child : node.children) {
            flattenInto(child, out);
        }
    }

    /**
     * Fail loudly when the retained tree violates the unique-node-id invariant
     * (spec/OPCODE.md §Frame Lifecycle). {@link #assignIds}/{@code nextId} is the
     * single id authority, so ordinary emission is always unique — but any pass that
     * overrides an id **after** {@code assignIds} (an identity-stable node pinning a
     * playback key, a proxy remap, a reused view subtree) can reintroduce a duplicate
     * and silently corrupt every id-keyed consumer. Throw instead.
     */
    private void guardUniqueIds() {
        List<Integer> duplicates = duplicateIds(tree);
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException(
                    "frame contains duplicate node id(s) " + duplicates
                            + ": a frame MUST NOT contain two nodes with the same id "
                            + "(spec/OPCODE.md §Frame Lifecycle)");
        }
    }

    /**
     * Materialize one non-visual {@code MEDIA_CONTROLLER} node per attached
     * {@link MediaController} (spec/MEDIA.md). An attach marker
     * ({@link MediaController#attach()}) *is* the controller node: it becomes the
     * MEDIA_CONTROLLER **in place**, so the controller lives exactly where the
     * application placed it — outside any swapping branch — and no structure is
     * synthesized.
     */
    private void materializeControllers(PathlandNode scanRoot) {
        List<PathlandNode> nodes = flatten(scanRoot);
        for (PathlandNode node : nodes) {
            MediaController controller = node.mediaControllerRef;
            if (controller != null && node.component == Components.COMMENT) {
                node.component = Components.MEDIA_CONTROLLER;
                applyController(node, controller);
                // The controller's node id is the playback key and MUST stay stable
                // across re-renders (a changing key would restart playback). Pin it
                // on first materialization and reuse it on every reconcile.
                Integer stable = mediaControllerIds.get(controller);
                if (stable == null) {
                    // Reserve the id before any further node is allocated, so a
                    // later create/reconcile can never reuse the playback key.
                    stable = nextFreeId();
                    mediaControllerIds.put(controller, stable);
                }
                node.id = stable;
                mediaControllers.put(controller, node);
            }
        }
        // Resolve a `VIDEO` surface's binding pre-diff (so the diff compares like for
        // like and never emits a late `MEDIA_CONTROLLER_ID` frame). A surface whose
        // controller was never attached stays unbound (blank, never a crash).
        for (PathlandNode node : nodes) {
            MediaController controller = node.mediaControllerRef;
            if (controller == null || node.component != Components.VIDEO) {
                continue;
            }
            Integer id = mediaControllerIds.get(controller);
            if (id != null) {
                node.properties.put(Properties.MEDIA_CONTROLLER_ID, id);
            }
        }
    }

    /** Bind a controller's transport signals to its `MEDIA_CONTROLLER` node, and
     *  route the renderer's `MEDIA_*` reports back into the controller's signals. */
    private void applyController(PathlandNode node, MediaController c) {
        node.properties.put(Properties.MEDIA_SOURCE, c.sourceSignal().get());
        node.propertyBindings.put(Properties.MEDIA_SOURCE, c.sourceSignal());
        node.properties.put(Properties.PLAYBACK_STATE, c.playing().get() ? 1 : 0);
        node.propertyBindings.put(Properties.PLAYBACK_STATE, c.playing());
        node.properties.put(Properties.MEDIA_POSITION, c.position().get());
        node.propertyBindings.put(Properties.MEDIA_POSITION, c.position());
        node.properties.put(Properties.MEDIA_VOLUME, c.volume().get());
        node.propertyBindings.put(Properties.MEDIA_VOLUME, c.volume());
        node.properties.put(Properties.MEDIA_MUTED, c.muted().get() ? 1 : 0);
        node.propertyBindings.put(Properties.MEDIA_MUTED, c.muted());
        node.properties.put(Properties.MEDIA_LOOP, c.loop().get() ? 1 : 0);
        node.propertyBindings.put(Properties.MEDIA_LOOP, c.loop());
        node.properties.put(Properties.MEDIA_RATE, c.rate().get());
        node.propertyBindings.put(Properties.MEDIA_RATE, c.rate());
        // Reports flow into the dedicated report signals (never echoed back as
        // commands, spec/EVENTS.md Media): writing the app's PLAYBACK_STATE
        // command from a report would, e.g., pause the next track after a
        // natural end.
        node.mediaInput = new MediaInput() {
            @Override
            public void onPlayStateChanged(boolean playing) {
                c.playingReport().set(playing);
            }

            @Override
            public void onTimeUpdated(float seconds) {
                c.time().set(seconds);
            }

            @Override
            public void onDurationChanged(float seconds) {
                c.duration().set(seconds);
            }

            @Override
            public void onEnded() {
                c.onEnded().run();
            }

            @Override
            public void onVolumeChanged(float volume) {
                c.volumeReport().set(volume);
            }
        };
    }

    private void emitNode(PathlandNode node) {
        sink.createNode(node.id, node.component);

        String text = node.textValue();
        if (text != null && !text.isEmpty()) {
            sink.setText(node.id, text);
        }
        if (node.dateBinding != null) {
            sink.setDate(node.id, node.days, node.millisOfDay);
        }
        ensureBindingId(node);
        for (Map.Entry<Integer, Object> entry : node.properties.entrySet()) {
            emitProperty(node.id, entry.getKey(), entry.getValue());
        }
        for (PathlandNode child : node.children) {
            emitNode(child);
            sink.insertChild(node.id, child.id, -1);
        }
    }

    private boolean ringSink() {
        return sink.requiresBindingId();
    }

    /**
     * Mark a bound control with {@code BINDING_ID} (the renderer's event gate).
     * The ring/desktop renderers gate {@code TEXT_CHANGED}/{@code VALUE_CHANGED} on
     * it; the frame (SSR/DOM) path never needs it. The value is the node's own id —
     * unique, and the host already routes events by target id.
     *
     * <p>Applied on <b>every</b> emission path (mount, reconcile of a matched node,
     * and a newly created subtree): a control materialized by a component slot
     * (e.g. the player bar's wide volume slider, created when its nested
     * {@code SIZE_THAT_FITS} swaps) must be gated too, or the renderer silently
     * drops its raw inputs.
     */
    private void ensureBindingId(PathlandNode node) {
        if (ringSink() && (node.textInput != null || node.valueInput != null || node.dateInput != null)) {
            node.properties.computeIfAbsent(Properties.BINDING_ID, k -> node.id);
        }
    }

    private void emitProperty(int nodeId, int property, Object value) {
        int valueType = value instanceof TokenPath token && token.tokenPath() != null
                ? ValueTypes.DESIGN_TOKEN
                : ValueTypes.forProperty(property);
        sink.setProperty(nodeId, property, valueType, value);
    }

    /** Value equality for a retained property diff. `float[]` (LIST-typed values,
     *  e.g. a SizeThatFits FIT_QUERY) are re-created on every render, so a
     *  reference comparison would re-emit an unchanged LIST on every reconcile —
     *  compare array contents instead ("identical recompute emits zero opcodes"). */
    private static boolean propertyValuesEqual(Object a, Object b) {
        if ((a instanceof float[] fa) && (b instanceof float[] fb)) {
            return Arrays.equals(fa, fb);
        }
        return Objects.equals(a, b);
    }

    private static boolean isConstant(Signal<?> signal) {
        return signal instanceof ConstantSignal;
    }

    private void trackBinding(ViewSlot slot, int nodeId, EffectRef effect) {
        slot.bindings.add(effect);
        bindings.add(effect);
        nodeBindings.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(effect);
    }

    private void registerNodeBindings(ViewSlot slot, PathlandNode node) {
        if (node.textBinding != null && !isConstant(node.textBinding)) {
            EffectRef effect = Signals.effect(() -> {
                String value = node.textBinding.get();
                if (!Objects.equals(value, node.text)) {
                    node.text = value;
                    sink.beginFrame();
                    sink.setText(node.id, value);
                    sink.endFrame();
                }
            });
            trackBinding(slot, node.id, effect);
        }
        if (node.dateBinding != null && !isConstant(node.dateBinding)) {
            EffectRef effect = Signals.effect(() -> {
                int days = node.dateBinding.get();
                if (days != node.days) {
                    node.days = days;
                    sink.beginFrame();
                    sink.setDate(node.id, days, node.millisOfDay);
                    sink.endFrame();
                }
            });
            trackBinding(slot, node.id, effect);
        }
        for (Map.Entry<Integer, Signal<?>> entry : node.propertyBindings.entrySet()) {
            int property = entry.getKey();
            Signal<?> signal = entry.getValue();
            // A constant signal never changes — its value was already written to the
            // node at mount, so no re-emit effect is needed.
            if (isConstant(signal)) {
                continue;
            }
            EffectRef effect = Signals.effect(() -> {
                Object value = ReactiveValues.from(property, signal);
                if (!Objects.equals(value, node.properties.get(property))) {
                    node.properties.put(property, value);
                    sink.beginFrame();
                    emitProperty(node.id, property, value);
                    sink.endFrame();
                }
            });
            trackBinding(slot, node.id, effect);
        }
        // Active-path listeners (onPathChange, spec DSL.md §4.5): fire on each change
        // (including the initial value). Node-level, destroyed on subtree replace.
        if (node.activePath != null && !node.pathChangeListeners.isEmpty()) {
            var listeners = java.util.List.copyOf(node.pathChangeListeners);
            EffectRef effect = Signals.effect(() -> {
                String path = node.activePath.get();
                for (java.util.function.Consumer<String> listener : listeners) {
                    listener.accept(path);
                }
            }, dev.pathland.view.signal.EffectOptions.allowWrites());
            trackBinding(slot, node.id, effect);
        }
    }

    /**
     * Diff a parent's old child list against a new one, assigning stable ids by
     * position + component type and recording the emission operations. Identical
     * structure produces no operations (zero opcodes).
     */
    private void reconcileChildren(
            PathlandNode parent, List<PathlandNode> oldList, List<PathlandNode> newList, List<Runnable> ops) {
        int n = Math.max(oldList.size(), newList.size());
        for (int i = 0; i < n; i++) {
            PathlandNode old = i < oldList.size() ? oldList.get(i) : null;
            PathlandNode fresh = i < newList.size() ? newList.get(i) : null;
            if (old != null && fresh != null && old.component == fresh.component) {
                fresh.id = old.id; // stable id: reused across the swap
                reconcileNode(old, fresh, ops);
            } else {
                if (old != null) {
                    int parentId = parent.id, oldId = old.id;
                    ops.add(() -> {
                        sink.removeChild(parentId, oldId);
                        sink.deleteNode(oldId);
                    });
                }
                if (fresh != null) {
                    if (fresh.id == 0) {
                        fresh.id = freshIdFor(fresh);
                    }
                    int id = fresh.id, component = fresh.component, index = i;
                    ops.add(() -> sink.createNode(id, component));
                    emitSubtreeInto(ops, fresh);
                    int parentId = parent.id;
                    ops.add(() -> sink.insertChild(parentId, id, index));
                }
            }
        }
    }

    /** Content diff for a matched (same component, same position) node pair. */
    private void reconcileNode(PathlandNode old, PathlandNode fresh, List<Runnable> ops) {
        // A matched control may have gained a binding since the previous emission
        // (or was first created before the gate existed): ensure the marker is in
        // the property set so the diff below emits it.
        ensureBindingId(fresh);
        String oldText = old.text; // last-emitted text (kept current by bindings)
        String freshText = currentText(fresh);
        if (!Objects.equals(oldText, freshText)) {
            int id = fresh.id;
            String text = freshText;
            ops.add(() -> sink.setText(id, text));
        }
        for (Map.Entry<Integer, Object> entry : fresh.properties.entrySet()) {
            if (!propertyValuesEqual(entry.getValue(), old.properties.get(entry.getKey()))) {
                int id = fresh.id, property = entry.getKey();
                Object value = entry.getValue();
                ops.add(() -> emitProperty(id, property, value));
            }
        }
        if (old.days != fresh.days || old.millisOfDay != fresh.millisOfDay) {
            int id = fresh.id, days = fresh.days, millis = fresh.millisOfDay;
            ops.add(() -> sink.setDate(id, days, millis));
        }
        reconcileChildren(fresh, old.children, fresh.children, ops);
    }

    /** Emit a newly created node's content + its whole child subtree (children get fresh ids). */
    private void emitSubtreeInto(List<Runnable> ops, PathlandNode node) {
        ensureBindingId(node);
        String text = currentText(node);
        if (text != null && !text.isEmpty()) {
            int id = node.id;
            String t = text;
            ops.add(() -> sink.setText(id, t));
        }
        if (node.dateBinding != null) {
            int id = node.id, days = node.days, millis = node.millisOfDay;
            ops.add(() -> sink.setDate(id, days, millis));
        }
        for (Map.Entry<Integer, Object> entry : node.properties.entrySet()) {
            int id = node.id, property = entry.getKey();
            Object value = entry.getValue();
            ops.add(() -> emitProperty(id, property, value));
        }
        for (PathlandNode child : node.children) {
            child.id = freshIdFor(child);
            int id = child.id, component = child.component;
            ops.add(() -> sink.createNode(id, component));
            emitSubtreeInto(ops, child);
            int parentId = node.id;
            ops.add(() -> sink.insertChild(parentId, id, -1));
        }
    }

    /** Current text value without recording a dependency (reconcile runs untracked). */
    private static String currentText(PathlandNode node) {
        if (node.textBinding != null) {
            return Signals.untracked(node.textBinding::get);
        }
        return node.text;
    }
}