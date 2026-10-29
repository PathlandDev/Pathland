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
import dev.pathland.view.transport.Event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The fine-grained emitter. Mounts a {@link View} tree once: assigns stable node ids,
 * emits the structural frame (TREE + PARAMETER), and registers a node-level binding effect
 * per reactive text/property. A signal change re-emits only that binding's
 * {@code SET_TEXT} / {@code SET_PROPERTY} — an unchanged signal emits zero opcodes.
 *
 * <p><b>Structural reactivity</b> (spec DSL.md §3.4): a node with a
 * {@link PathlandNode#structuralContent} slot is a <em>structural container</em> —
 * its single child subtree is selected by a signal and reconciled by a structural
 * effect. On selector change the emitter re-evaluates the content, diffs the retained
 * subtree, and emits only {@code TREE} deltas (CREATE/DELETE/INSERT); an identical
 * recompute emits zero opcodes. The slot's own subtree bindings are owned by that
 * effect, so nested structural containers work.
 *
 * <p>This mirrors the Rust engine's signal dependency graph in Java: the retained tree
 * and its signal bindings live here, and the {@link OpcodeSink} (frame or ring) carries
 * only the delta opcodes.
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

    /** Whether the next full emission (mount/resync) renders `SIZE_THAT_FITS`
     *  slots with ALL candidates as children (the web SSR container-query first
     *  paint, issue #141). Set by the SSR session before mount; internal only —
     *  the authored DSL and the live reconcile path are unchanged. */
    private boolean snapshotEmission;

    /** Fired after a structural reconcile (see {@link #onStructureReconciled}). */
    private Runnable structureReconciled;

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

    /** Mount the view tree: assign ids, emit the structural frame, register bindings. */
    public RenderResult mount(View root, Environment env) {
        if (mounted) {
            throw new IllegalStateException("Emitter already mounted; create a new Emitter per tree");
        }
        mounted = true;
        this.env = env;

        // A full-snapshot (SSR) emission renders fit slots with ALL candidates so
        // the web SSR shows every branch (CSS container queries gate the first
        // paint); the live reconcile path keeps one child. The flag is scoped to
        // this render via a thread-local read by SizeThatFits.FitSlot.
        boolean prevSnapshot = PathlandNode.SNAPSHOT_EMISSION.get();
        PathlandNode.SNAPSHOT_EMISSION.set(snapshotEmission);
        PathlandNode tree;
        try {
            tree = root.render(env);
        } finally {
            PathlandNode.SNAPSHOT_EMISSION.set(prevSnapshot);
        }
        this.tree = tree;
        this.windowMetadata = env.windowMetadata();
        nextId = 1;
        assignIds(tree);
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
        collectInputs(tree);

        sink.beginFrame();
        emitTheme();
        emitWindowMetadata(false);
        emitNode(tree);
        sink.endFrame();

        bindings.clear();
        nodeBindings.clear();
        registerBindings(tree);
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

    /** Collect tap actions + input sinks, keyed by node id, for the host's event routing. */
    private void collectInputs(PathlandNode node) {
        collectInputs(node, null);
    }

    private void collectInputs(PathlandNode node, dev.pathland.view.router.Router enclosing) {
        // Descend into a NavigationContainer: its router becomes the nearest enclosing
        // one for everything below it (nested containers override the outer router).
        if (node.router != null) {
            enclosing = node.router;
        }
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
            fitInputs.put(node.id, node.fitInput);
        }
        if (node.proxyConfig != null) {
            proxyViews.put(node.id, node.proxyConfig);
        }
        if (node.navigateHandler != null) {
            navigateHandler = node.navigateHandler; // global: a NavigationContainer's router sink
        }
        // A declared navigation intent resolves to the nearest enclosing router.
        if (node.navigateTo != null && enclosing != null) {
            String to = node.navigateTo;
            dev.pathland.view.router.NavOp op = node.navOp;
            int id = node.id;
            dev.pathland.view.router.Router router = enclosing;
            navigateActions.put(id, () -> op.applyTo(router, to));
        }
        for (PathlandNode child : node.children) {
            collectInputs(child, enclosing);
        }
    }

    /** Drop input routing entries for a subtree being removed (ids stay for reused nodes). */
    private void forgetInputs(List<PathlandNode> nodes) {
        for (PathlandNode node : nodes) {
            forgetInputs(node);
        }
    }

    private void forgetInputs(PathlandNode node) {
        tapActions.remove(node.id);
        textInputs.remove(node.id);
        valueInputs.remove(node.id);
        editingInputs.remove(node.id);
        dateInputs.remove(node.id);
        mediaInputs.remove(node.id);
        fitInputs.remove(node.id);
        navigateActions.remove(node.id);
        proxyViews.remove(node.id);
        for (PathlandNode child : node.children) {
            forgetInputs(child);
        }
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
     * and a newly created subtree): a control materialized by a structural slot
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

    /**
     * Register a node-level binding effect for every reactive text/property in the
     * tree. When the bound signal changes, only that node's delta opcode is emitted.
     * The effect's first (immediate) run reads the current value and finds it equal to
     * the node's value already emitted at mount, so no duplicate opcode is produced.
     *
     * <p>A structural slot ({@link PathlandNode#structuralContent}) does not recurse
     * into its child subtree here: its structural effect owns that subtree entirely
     * (it re-registers bindings after each reconcile), which keeps nested structural
     * containers correct.
     */
    private void registerBindings(PathlandNode node) {
        registerNodeBindings(node);
        if (node.structuralContent != null) {
            EffectRef structural = Signals.effect(() -> reconcileSlot(node));
            bindings.add(structural);
            nodeBindings.computeIfAbsent(node.id, k -> new ArrayList<>()).add(structural);
            // A full-snapshot fit slot (web SSR, issue #141) has every candidate as
            // a child before the renderer reports; register their bindings too so
            // the CSS-visible candidate stays reactive in the multi-candidate form.
            // On the first FIT_CHANGED the reconcile collapses to one child and
            // re-registers just that subtree (zero-op identical recompute).
            if (node.children.size() > 1) {
                for (PathlandNode child : node.children) {
                    registerBindings(child);
                }
            }
        } else {
            for (PathlandNode child : node.children) {
                registerBindings(child);
            }
        }
    }

    private static boolean isConstant(Signal<?> signal) {
        return signal instanceof ConstantSignal;
    }

    private void registerNodeBindings(PathlandNode node) {
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
            bindings.add(effect);
            nodeBindings.computeIfAbsent(node.id, k -> new ArrayList<>()).add(effect);
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
            bindings.add(effect);
            nodeBindings.computeIfAbsent(node.id, k -> new ArrayList<>()).add(effect);
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
            bindings.add(effect);
            nodeBindings.computeIfAbsent(node.id, k -> new ArrayList<>()).add(effect);
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
            bindings.add(effect);
            nodeBindings.computeIfAbsent(node.id, k -> new ArrayList<>()).add(effect);
        }
    }

    /**
     * Reconcile a structural slot's child subtree against the retained one. Runs inside
     * a {@code Signals.effect}: the content supplier reads the selector signal (the
     * tracked dependency), then renders the new content <em>untracked</em> so reactive
     * values inside it stay fine-grained (owned by their own node-level binding effects).
     */
    private void reconcileSlot(PathlandNode slot) {
        // Read the selector FIRST (the tracked dependency) so this structural effect
        // subscribes to the fit-index signal; the gate below must not starve it.
        View content = slot.structuralContent.get(); // tracked: subscribes to the selector
        // A full-snapshot fit slot (issue #141) carries every candidate until the
        // renderer reports. The structural effect fires immediately at mount AND
        // on the selector read — before the first FIT_CHANGED we must keep the
        // multi-candidate presentation (the web SSR's container-query first paint),
        // so a no-op gate collapses nothing. The first FIT_CHANGED sets fitReported
        // (before the incoming index) → the gate opens → the normal reconcile
        // below downgrades N → 1 selected child.
        if (slot.children.size() > 1 && !slot.fitReported) {
            return;
        }
        // Snapshot → live downgrade (issue #141): the slot carried EVERY candidate
        // (in FIT_QUERY order); now keep the SELECTED candidate's node as-is and
        // remove the others. NEVER positionally diff here — both candidates are
        // frequently the same component, and morphing the loser's node into the
        // winner would relocate the winner into the SSR's LOSER wrapper, breaking
        // the renderer's candidate-position CSS gate (the wide content would end up
        // behind the hidden wrapper → blank page). Keeping the selected node also
        // makes the transition zero-churn (its id + subtree are retained).
        if (slot.children.size() > 1) {
            int keepIndex = Math.min(Math.max(slot.fitSelection, 0), slot.children.size() - 1);
            PathlandNode keep = slot.children.get(keepIndex);
            List<PathlandNode> dropped = new ArrayList<>(slot.children);
            dropped.remove(keepIndex);
            destroySubtreeBindings(dropped);
            forgetInputs(dropped);
            slot.children.clear();
            slot.children.add(keep);
            if (!dropped.isEmpty()) {
                sink.beginFrame();
                for (PathlandNode old : dropped) {
                    int parentId = slot.id, oldId = old.id;
                    sink.removeChild(parentId, oldId);
                    sink.deleteNode(oldId);
                }
                sink.endFrame();
            }
            // A **shared** subtree (e.g. the music player's mainArea, which holds the
            // media controller) is rendered once per candidate, so the kept and
            // dropped subtrees contain nodes with the SAME ids. `forgetInputs(dropped)`
            // therefore removed the kept subtree's routing entries too (tap actions,
            // media sinks, …) — re-collect them, or a controller's media reports are
            // silently dropped after the snapshot→live downgrade (spec/MEDIA.md).
            collectInputs(keep, slot.router);
            guardUniqueIds();
            fireStructureReconciled();
            return;
        }
        // Re-apply the scope the destination should see (the slot's incoming
        // environment + any slot-provided bindings like the router), so destinations
        // that read environment values keep working on every re-render.
        EnvironmentValues envScope = slot.environmentForChildren;
        EnvironmentValues previous = Environment.current();
        PathlandNode fresh;
        if (envScope != null) {
            Environment.within(envScope);
        }
        try {
            fresh = content == null ? null : Signals.untracked(() -> content.render(env));
        } finally {
            Environment.restore(previous);
        }
        List<PathlandNode> oldChildren = slot.children;
        List<PathlandNode> newChildren = fresh == null ? List.of() : List.of(fresh);

        destroySubtreeBindings(oldChildren);
        forgetInputs(oldChildren);

        List<Runnable> ops = new ArrayList<>();
        // Materialize the fresh subtree's controllers in place (binds nothing yet:
        // ids are assigned by the diff below).
        if (fresh != null) {
            materializeControllers(fresh);
        }
        reconcileChildren(slot, oldChildren, newChildren, ops);
        slot.children.clear();
        slot.children.addAll(newChildren);

        // Coalesce a slot-level STRING property change (e.g. ROUTE) into the same frame.
        if (slot.structuralStringProperty != null) {
            int property = slot.structuralStringProperty;
            String value = Signals.untracked(slot.structuralStringValue);
            if (!Objects.equals(value, slot.properties.get(property))) {
                slot.properties.put(property, value);
                int id = slot.id;
                ops.add(() -> emitProperty(id, property, value));
            }
        }
        // Coalesce a slot-level U32 property change (e.g. NAV_DEPTH) the same way.
        if (slot.structuralU32Property != null) {
            int property = slot.structuralU32Property;
            Integer value = Signals.untracked(slot.structuralU32Value);
            if (!Objects.equals(value, slot.properties.get(property))) {
                slot.properties.put(property, value);
                int id = slot.id;
                ops.add(() -> emitProperty(id, property, value));
            }
        }

        if (!ops.isEmpty()) {
            sink.beginFrame();
            for (Runnable op : ops) {
                op.run();
            }
            sink.endFrame();
        }

        if (fresh != null) {
            // Re-register the destination subtree's routing under the slot's own router
            // (a NavigationContainer's router), so declarative nav intents resolve even
            // after a structural reconcile (spec DSL.md §4.5).
            collectInputs(fresh, slot.router);
            registerBindings(fresh);
        }
        // The routing maps (incl. proxyViews) now reflect the reconciled subtree.
        guardUniqueIds();
        fireStructureReconciled();
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
                // A nested structural slot is retained state: re-rendering its parent
                // subtree (an outer slot's candidate swap, a custom style's body re-run)
                // re-instantiates the inner slot's View with a DEFAULT selection. Carry
                // the previously selected candidate across the match before the diff —
                // otherwise a nested fit (the player bar's controls slot under the root
                // row) resets to compact and stays there (the DOM client never re-reports
                // an unchanged width). "Identical recompute" then still emits zero ops.
                if (fresh.structuralContent != null && old.structuralContent != null
                        && old.fitSelection != fresh.fitSelection) {
                    restoreFitSelection(old, fresh);
                }
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

    /**
     * Carry a nested structural slot's selection across a parent-subtree re-render.
     * Re-rendering the parent (an outer slot's candidate swap, a custom style's body
     * re-run) re-instantiates the inner slot with a DEFAULT selection (index 0), so
     * re-apply the retained index to the fresh slot's signal and re-render its chosen
     * candidate BEFORE the diff — the diff then compares like for like and emits
     * nothing (a nested fit keeps its candidate; "identical recompute" stays zero-op).
     */
    private void restoreFitSelection(PathlandNode old, PathlandNode fresh) {
        int selected = old.fitSelection;
        if (fresh.fitInput != null) {
            // The reconcile runs inside a structural slot's effect, where direct signal
            // writes are guarded — this is a deliberate retained-selection write, so
            // scope it out of the reactive context (`untracked` clears the current
            // node): the fresh slot's signal gets the carried index without tripping
            // the write guard, and no consumer is flushed yet (the fresh subtree's
            // bindings are (re)registered after the reconcile).
            Signals.untracked(() -> {
                fresh.fitInput.accept(selected);
                return null;
            });
        }
        fresh.fitSelection = selected;
        fresh.children.clear();
        PathlandNode content = renderStructuralContent(fresh);
        if (content != null) {
            fresh.children.add(content);
        }
    }

    /** Render a structural slot's currently selected candidate within its captured scope. */
    private PathlandNode renderStructuralContent(PathlandNode slot) {
        EnvironmentValues envScope = slot.environmentForChildren;
        EnvironmentValues previous = Environment.current();
        if (envScope != null) {
            Environment.within(envScope);
        }
        try {
            return Signals.untracked(() -> {
                View view = slot.structuralContent == null ? null : slot.structuralContent.get();
                return view == null ? null : view.render(env);
            });
        } finally {
            Environment.restore(previous);
        }
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

    /** Destroy the binding effects owned by a subtree (before it is replaced). */
    private void destroySubtreeBindings(List<PathlandNode> nodes) {
        for (PathlandNode node : nodes) {
            destroySubtreeBindings(node);
        }
    }

    private void destroySubtreeBindings(PathlandNode node) {
        List<EffectRef> refs = nodeBindings.remove(node.id);
        if (refs != null) {
            for (EffectRef ref : refs) {
                ref.destroy();
            }
            bindings.removeAll(refs);
        }
        for (PathlandNode child : node.children) {
            destroySubtreeBindings(child);
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