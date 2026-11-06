package dev.pathland.view.engine

import dev.pathland.markdown.Markdown
import dev.pathland.view.core.AdaptiveTheme
import dev.pathland.view.core.Color
import dev.pathland.view.core.Environment
import dev.pathland.view.core.EnvironmentValues
import dev.pathland.view.core.MediaController
import dev.pathland.view.core.Platform
import dev.pathland.view.core.ProxyConfig
import dev.pathland.view.core.Theme
import dev.pathland.view.core.ThemeData
import dev.pathland.view.core.ThemeMode
import dev.pathland.view.core.View
import dev.pathland.view.core.WindowMetadata
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.core.protocol.Components
import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.core.protocol.TokenPath
import dev.pathland.view.core.protocol.ValueTypes
import dev.pathland.view.core.router.NavOp
import dev.pathland.view.core.router.Router
import dev.pathland.view.core.signal.ConstantSignal
import dev.pathland.view.core.signal.EffectOptions
import dev.pathland.view.core.signal.EffectRef
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.signal.Tracked
import dev.pathland.view.core.transport.Event
import dev.pathland.view.core.transport.OpcodeSink
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Objects
import java.util.function.Consumer

/** The component-slot emitter: mounts a View tree and diff-emits only changes. */
class Emitter {

    private val sink: OpcodeSink
    private var theme: ThemeData?
    private var windowMetadata: WindowMetadata? = null
    private val lastWindowMetadata = LinkedHashMap<Int, String>()
    private val tapActions = LinkedHashMap<Int, Runnable>()
    private val textInputs = LinkedHashMap<Int, Consumer<String>>()
    private val valueInputs = LinkedHashMap<Int, Consumer<Float>>()
    private val editingInputs = LinkedHashMap<Int, Consumer<Boolean>>()
    private val dateInputs = LinkedHashMap<Int, DateInput>()
    private val mediaInputs = LinkedHashMap<Int, MediaInput>()
    private val fitInputs = LinkedHashMap<Int, Consumer<Int>>()
    private val navigateActions = LinkedHashMap<Int, Runnable>()
    private val proxyViews = LinkedHashMap<Int, ProxyConfig>()
    private val registeredMediaControllers = LinkedHashSet<MediaController>()
    private val mediaControllers = IdentityHashMap<MediaController, PathlandNode>()
    private val mediaControllerIds = IdentityHashMap<MediaController, Int>()
    private val bindings = ArrayList<EffectRef>()
    private val nodeBindings = LinkedHashMap<Int, MutableList<EffectRef>>()
    private var navigateHandler: Consumer<Event>? = null
    private var nextId = 1
    private var rootId = 0
    private var mounted = false
    private var tree: PathlandNode? = null
    private var env: Environment? = null
    private var rootSlot: ViewSlot? = null
    private val renderStack = ArrayDeque<ViewSlot>()

    private var snapshotEmission = false
    private var snapshotActive = false
    private var structureReconciled: Runnable? = null

    constructor(sink: OpcodeSink) : this(sink, null)

    constructor(sink: OpcodeSink, theme: ThemeData?) {
        this.sink = Objects.requireNonNull(sink, "sink")
        this.theme = theme
    }

    /** Render one component slot (see `View.render`). */
    fun renderView(view: View, env: Environment): PathlandNode {
        val parent = renderStack.peek()
        val slot = ViewSlot(view, env, Environment.current(), parent)
        if (parent != null) {
            slot.oldNodeHint = oldChildHint(parent, parent.renderingChildren?.size ?: 0)
            parent.addRenderingChild(slot)
        }
        val tracked = Signals.tracked(
            { renderSlot(slot) },
            { fresh -> reconcileSlot(slot, fresh) },
        )
        slot.tracked = tracked
        bindings.add(tracked)
        var node = tracked.initial()
        if (node == null) {
            node = PathlandNode(Components.VSTACK)
        }
        val owned = node.slot
        if (owned != null && owned !== slot && slot.renderingChildren?.contains(owned) == true) {
            throw IllegalStateException(
                "View ${view.javaClass.name} returned a node owned by a child slot; " +
                    "use renderNode(env) (not render(env)) for root-chain delegation",
            )
        }
        slot.node = node
        node.slot = slot
        slot.finishFirstRender()
        slot.oldNodeHint = null
        if (parent == null) rootSlot = slot
        return node
    }

    private fun renderSlot(slot: ViewSlot): PathlandNode {
        val previous = CURRENT.get()
        CURRENT.set(this)
        renderStack.push(slot)
        val previousEnv = Environment.current()
        Environment.within(slot.envScope)
        val previousSnapshot = PathlandNode.SNAPSHOT_EMISSION.get()
        PathlandNode.SNAPSHOT_EMISSION.set(snapshotActive)
        try {
            slot.beginRenderChildren()
            return slot.view.renderNode(slot.env)
        } finally {
            PathlandNode.SNAPSHOT_EMISSION.set(previousSnapshot)
            Environment.restore(previousEnv)
            renderStack.pop()
            CURRENT.set(previous)
        }
    }

    private fun oldChildHint(parent: ViewSlot, index: Int): PathlandNode? {
        val oldParent = parent.node ?: parent.oldNodeHint ?: return null
        if (index < 0 || index >= oldParent.children.size) return null
        return oldParent.children[index]
    }

    /** The fit selection the current slot should inherit from the node it replaced. */
    fun currentSlotOldFitSelection(): Int? {
        val slot = renderStack.peek() ?: return null
        val hint = slot.oldNodeHint ?: return null
        if (hint.component != Components.SIZE_THAT_FITS) return null
        return hint.fitSelection
    }

    fun snapshotEmission(on: Boolean) {
        snapshotEmission = on
    }

    fun onStructureReconciled(callback: Runnable) {
        structureReconciled = callback
    }

    private fun fireStructureReconciled() {
        structureReconciled?.run()
    }

    // ── slots ──────────────────────────────────────────────────────────────

    private fun linkSlots(root: PathlandNode) {
        linkSlots(root, null, -1)
    }

    private fun linkSlots(node: PathlandNode, parent: PathlandNode?, index: Int) {
        node.parent = parent
        node.index = index
        node.slot?.let {
            it.nodeParent = parent
            it.nodeIndex = index
        }
        for (i in node.children.indices) {
            linkSlots(node.children[i], node, i)
        }
    }

    private fun reconcileSlot(slot: ViewSlot, freshIn: PathlandNode?) {
        val oldChildren = slot.children
        val newChildren = slot.takeRenderedChildren()
        var fresh = freshIn ?: PathlandNode(Components.VSTACK)
        fresh.slot = slot
        Signals.untracked { collectVideoControllers(fresh); null }
        if (slot.parent == null) {
            appendRootControllers(fresh)
        } else {
            tree?.let { syncRootControllers(it) }
        }
        bindVideoControllers(fresh)
        val old = slot.node
        destroySlotBindings(slot)
        forgetSlotInputs(slot)

        if (old != null && old.component == Components.SIZE_THAT_FITS &&
            fresh.component == Components.SIZE_THAT_FITS &&
            old.children.count { !isControllerNode(it) } > 1 && newChildren.size == 1
        ) {
            reconcileSnapshotFit(slot, old, fresh, oldChildren, newChildren)
            return
        }

        for (child in oldChildren) destroySlotTree(child)

        val ops = ArrayList<Runnable>()
        if (old != null && old.component == fresh.component) {
            fresh.id = old.id
            reconcileNode(old, fresh, ops)
        } else {
            val parent = slot.nodeParent
                ?: throw IllegalStateException(
                    "the root view's component type must stay stable across reactive re-renders; " +
                        "wrap it in a stable container (e.g. View.of or VStack)",
                )
            val oldId = old!!.id
            val parentId = parent.id
            ops.add(Runnable {
                sink.removeChild(parentId, oldId)
                sink.deleteNode(oldId)
            })
            fresh.id = freshIdFor(fresh)
            emitSubtreeInto(ops, fresh, parentId, slot.nodeIndex)
        }

        if (ops.isNotEmpty()) {
            sink.beginFrame()
            for (op in ops) op.run()
            sink.endFrame()
        }

        slot.node = fresh
        slot.children.clear()
        slot.children.addAll(newChildren)
        val parent = slot.nodeParent
        if (parent != null) parent.children[slot.nodeIndex] = fresh else tree = fresh
        linkSlots(fresh)
        registerSlotTree(slot)
        collectSlotTree(slot)
        refreshNavigateHandler()
        guardUniqueIds()
        fireStructureReconciled()
    }

    private fun reconcileSnapshotFit(
        slot: ViewSlot,
        old: PathlandNode,
        fresh: PathlandNode,
        oldChildren: List<ViewSlot>,
        newChildren: List<ViewSlot>,
    ) {
        val candidates = old.children.filterNot { isControllerNode(it) }
        val selected = minOf(maxOf(fresh.fitSelection, 0), candidates.size - 1)
        val keep = candidates[selected]
        val keepSlot = keep.slot
        val controllers = LinkedHashSet<PathlandNode>()
        controllers.addAll(old.children.filter {
            isControllerNode(it) && registeredMediaControllers.contains(it.mediaControllerRef)
        })
        controllers.addAll(fresh.children.filter {
            isControllerNode(it) && registeredMediaControllers.contains(it.mediaControllerRef)
        })
        val dropOps = ArrayList<Runnable>()
        for (child in old.children) {
            if (child === keep) continue
            if (isControllerNode(child) &&
                registeredMediaControllers.contains(child.mediaControllerRef)
            ) continue
            val parentId = old.id
            val childId = child.id
            dropOps.add(Runnable {
                sink.removeChild(parentId, childId)
                sink.deleteNode(childId)
            })
        }
        for (child in oldChildren) if (child !== keepSlot) destroySlotTree(child)
        for (child in newChildren) destroySlotTree(child)
        old.children.clear()
        old.children.add(keep)
        old.children.addAll(controllers)
        fresh.children.clear()
        fresh.children.add(keep)
        fresh.children.addAll(controllers)
        slot.children.clear()
        if (keepSlot != null) slot.children.add(keepSlot)
        fresh.id = old.id
        if (dropOps.isNotEmpty()) {
            sink.beginFrame()
            for (op in dropOps) op.run()
            sink.endFrame()
        }
        val ops = ArrayList<Runnable>()
        reconcileNode(old, fresh, ops)
        if (ops.isNotEmpty()) {
            sink.beginFrame()
            for (op in ops) op.run()
            sink.endFrame()
        }
        slot.node = fresh
        val parent = slot.nodeParent
        if (parent != null) parent.children[slot.nodeIndex] = fresh else tree = fresh
        linkSlots(fresh)
        registerSlotBindings(slot)
        collectSlotInputs(slot, null)
        refreshNavigateHandler()
        guardUniqueIds()
        fireStructureReconciled()
    }

    private fun registerSlotTree(slot: ViewSlot) {
        registerSlotBindings(slot)
        for (child in slot.children) registerSlotTree(child)
    }

    private fun registerSlotBindings(slot: ViewSlot) {
        val node = slot.node ?: return
        walkOwned(node, slot) { registerNodeBindings(slot, it) }
    }

    private fun collectSlotTree(slot: ViewSlot) {
        collectSlotInputs(slot, null)
    }

    private fun collectSlotInputs(slot: ViewSlot, enclosing: Router?): Router? {
        var scope = enclosing
        val node = slot.node
        if (node != null) scope = collectOwnedInputs(node, slot, enclosing)
        for (child in slot.children) collectSlotInputs(child, scope)
        return scope
    }

    private fun collectOwnedInputs(node: PathlandNode, slot: ViewSlot, enclosing: Router?): Router? {
        val owner = node.slot
        if (owner != null && owner !== slot) return enclosing
        var scope = node.router ?: enclosing
        collectInputsForNode(node, scope)
        for (child in node.children) {
            scope = collectOwnedInputs(child, slot, scope)
        }
        return scope
    }

    private fun collectInputsForNode(node: PathlandNode, enclosing: Router?) {
        for (action in node.tapActions) tapActions[node.id] = action
        node.textInput?.let { textInputs[node.id] = it }
        node.valueInput?.let { valueInputs[node.id] = it }
        node.editingInput?.let { editingInputs[node.id] = it }
        node.dateInput?.let { dateInputs[node.id] = it }
        node.mediaInput?.let { mediaInputs[node.id] = it }
        node.fitInput?.let { inner ->
            fitInputs[node.id] = Consumer { index ->
                snapshotActive = false
                inner.accept(index)
            }
        }
        node.proxyConfig?.let { proxyViews[node.id] = it }
        node.navigateHandler?.let { navigateHandler = it }
        val to = node.navigateTo
        if (to != null && enclosing != null) {
            val op = node.navOp
            val id = node.id
            val router = enclosing
            navigateActions[id] = Runnable { op.applyTo(router, to) }
        }
    }

    private fun refreshNavigateHandler() {
        navigateHandler = null
        tree?.let { collectNavigateHandler(it) }
    }

    private fun collectNavigateHandler(node: PathlandNode) {
        node.navigateHandler?.let { navigateHandler = it }
        for (child in node.children) collectNavigateHandler(child)
    }

    private fun destroySlotTree(slot: ViewSlot) {
        for (child in slot.children) destroySlotTree(child)
        destroySlotBindings(slot)
        forgetSlotInputs(slot)
        val tracked = slot.tracked
        if (tracked != null) {
            slot.tracked = null
            bindings.remove(tracked)
            tracked.destroy()
        }
    }

    private fun destroySlotBindings(slot: ViewSlot) {
        for (effect in slot.bindings) {
            effect.destroy()
            bindings.remove(effect)
        }
        slot.bindings.clear()
        val node = slot.node
        if (node != null) walkOwned(node, slot) { nodeBindings.remove(it.id) }
    }

    private fun forgetSlotInputs(slot: ViewSlot) {
        val node = slot.node ?: return
        walkOwned(node, slot) { forgetInputsForNode(it) }
    }

    private fun forgetInputsForNode(node: PathlandNode) {
        tapActions.remove(node.id)
        textInputs.remove(node.id)
        valueInputs.remove(node.id)
        editingInputs.remove(node.id)
        dateInputs.remove(node.id)
        mediaInputs.remove(node.id)
        fitInputs.remove(node.id)
        navigateActions.remove(node.id)
        proxyViews.remove(node.id)
    }

    private fun walkOwned(node: PathlandNode, slot: ViewSlot, visit: (PathlandNode) -> Unit) {
        val owner = node.slot
        if (owner != null && owner !== slot) return
        visit(node)
        for (child in node.children) walkOwned(child, slot, visit)
    }

    /** Mount the view tree. */
    fun mount(root: View, env: Environment): RenderResult {
        check(!mounted) { "Emitter already mounted; create a new Emitter per tree" }
        mounted = true
        this.env = env
        this.snapshotActive = snapshotEmission
        val previous = CURRENT.get()
        CURRENT.set(this)
        val tree: PathlandNode
        try {
            tree = root.render(env)
        } finally {
            CURRENT.set(previous)
        }
        this.tree = tree
        this.windowMetadata = env.windowMetadata()
        nextId = 1
        assignIds(tree)
        mediaControllers.clear()
        mediaControllerIds.clear()
        collectVideoControllers(tree)
        appendRootControllers(tree)
        bindVideoControllers(tree)
        linkSlots(tree)
        guardUniqueIds()
        tapActions.clear()
        textInputs.clear()
        valueInputs.clear()
        editingInputs.clear()
        dateInputs.clear()
        mediaInputs.clear()
        fitInputs.clear()
        navigateActions.clear()
        proxyViews.clear()
        navigateHandler = null
        collectSlotTree(rootSlot!!)

        sink.beginFrame()
        emitTheme()
        emitWindowMetadata(false)
        emitNode(tree)
        sink.endFrame()

        registerSlotTree(rootSlot!!)
        registerWindowMetadataBindings()
        rootId = tree.id
        return RenderResult(
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
            Collections.unmodifiableMap(proxyViews),
        )
    }

    fun rootId(): Int = rootId

    /**
     * Project [controller] as a pinned, non-visual root node. Registration is
     * idempotent; outside a render it is applied immediately, during a render it
     * is folded into the current reconcile.
     */
    fun registerMediaController(controller: MediaController) {
        if (!registeredMediaControllers.add(controller)) return
        if (mounted && renderStack.isEmpty()) tree?.let { syncRootControllers(it) }
    }

    /** Remove [controller] from the root projection. */
    fun unregisterMediaController(controller: MediaController) {
        if (!registeredMediaControllers.remove(controller)) return
        if (mounted && renderStack.isEmpty()) tree?.let { syncRootControllers(it) }
    }

    /** Re-emit the complete retained tree as one full-snapshot frame. */
    fun renderFull() {
        check(mounted) { "Emitter not mounted" }
        guardUniqueIds()
        sink.beginFrame()
        emitTheme()
        emitWindowMetadata(true)
        emitNode(tree!!)
        sink.endFrame()
    }

    private fun emitTheme() {
        theme?.emitInto(sink)
    }

    private fun emitWindowMetadata(force: Boolean) {
        val metadata = windowMetadata ?: return
        emitWindowField(Commands.WindowFields.TITLE, metadata.title(), force)
        emitWindowField(Commands.WindowFields.DESCRIPTION, metadata.description(), force)
        emitWindowField(Commands.WindowFields.LOCALE, metadata.locale(), force)
    }

    private fun emitWindowField(field: Int, signal: Signal<String>?, force: Boolean) {
        var value: String
        if (signal == null) {
            if (!lastWindowMetadata.containsKey(field) && !force) return
            value = ""
        } else {
            value = signal.get() ?: ""
        }
        if (force || lastWindowMetadata[field] != value) {
            sink.setWindowMetadata(field, value)
            lastWindowMetadata[field] = value
        }
    }

    private fun registerWindowMetadataBindings() {
        val metadata = windowMetadata ?: return
        bindWindowField(Commands.WindowFields.TITLE, metadata.title())
        bindWindowField(Commands.WindowFields.DESCRIPTION, metadata.description())
        bindWindowField(Commands.WindowFields.LOCALE, metadata.locale())
    }

    private fun bindWindowField(field: Int, signal: Signal<String>?) {
        if (signal == null || isConstant(signal)) return
        val effect = Signals.effect {
            val value = signal.get() ?: ""
            if (lastWindowMetadata[field] != value) {
                lastWindowMetadata[field] = value
                sink.beginFrame()
                sink.setWindowMetadata(field, value)
                sink.endFrame()
            }
        }
        bindings.add(effect)
    }

    fun replaceTheme(theme: ThemeData?) {
        this.theme = theme
        sink.beginFrame()
        sink.resetTheme()
        emitTheme()
        sink.endFrame()
    }

    fun setColorScheme(mode: ThemeMode) {
        sink.beginFrame()
        sink.setColorScheme(mode.code())
        sink.endFrame()
    }

    fun resetTheme() {
        replaceTheme(null)
    }

    fun destroy() {
        rootSlot?.let {
            destroySlotTree(it)
            rootSlot = null
        }
        for (effect in bindings) effect.destroy()
        bindings.clear()
        nodeBindings.clear()
        registeredMediaControllers.clear()
        mediaControllers.clear()
        mediaControllerIds.clear()
    }

    private fun assignIds(node: PathlandNode) {
        node.id = nextFreeId()
        for (child in node.children) assignIds(child)
    }

    private fun nextFreeId(): Int {
        while (mediaControllerIds.containsValue(nextId)) nextId++
        return nextId++
    }

    private fun freshIdFor(node: PathlandNode): Int {
        val controller = node.mediaControllerRef
        if (controller != null && node.component == Components.MEDIA_CONTROLLER) {
            mediaControllerIds[controller]?.let { return it }
        }
        return nextFreeId()
    }

    private fun flatten(node: PathlandNode): List<PathlandNode> {
        val out = ArrayList<PathlandNode>()
        flattenInto(node, out)
        return out
    }

    private fun flattenInto(node: PathlandNode, out: MutableList<PathlandNode>) {
        out.add(node)
        for (child in node.children) flattenInto(child, out)
    }

    private fun guardUniqueIds() {
        val duplicates = duplicateIds(tree!!)
        check(duplicates.isEmpty()) {
            "frame contains duplicate node id(s) $duplicates: a frame MUST NOT contain two " +
                "nodes with the same id (spec/OPCODE.md §Frame Lifecycle)"
        }
    }

    private fun isControllerNode(node: PathlandNode): Boolean =
        node.component == Components.MEDIA_CONTROLLER && node.mediaControllerRef != null

    /** A `VIDEO` surface references its controller, so it also keeps it registered. */
    private fun collectVideoControllers(scanRoot: PathlandNode) {
        for (node in flatten(scanRoot)) {
            if (node.component == Components.VIDEO) {
                node.mediaControllerRef?.let { registeredMediaControllers.add(it) }
            }
        }
    }

    /**
     * Append the registered controllers to a root node. Existing controller nodes
     * are reused by identity, so the root reconcile preserves their ids and does
     * not restart playback.
     */
    private fun appendRootControllers(root: PathlandNode) {
        for (controller in registeredMediaControllers) {
            val existing = mediaControllers[controller]
            if (existing != null) {
                if (root.children.none { it === existing }) root.children.add(existing)
                continue
            }
            val node = newControllerNode(controller)
            mediaControllers[controller] = node
            root.children.add(node)
        }
    }

    private fun newControllerNode(controller: MediaController): PathlandNode {
        val node = PathlandNode(Components.MEDIA_CONTROLLER)
        node.mediaControllerRef = controller
        applyController(node, controller)
        val stable = mediaControllerIds[controller]
        node.id = stable ?: nextFreeId().also { mediaControllerIds[controller] = it }
        return node
    }

    private fun bindVideoControllers(scanRoot: PathlandNode) {
        for (node in flatten(scanRoot)) {
            val controller = node.mediaControllerRef
            if (controller == null || node.component != Components.VIDEO) continue
            mediaControllerIds[controller]?.let { node.properties[Properties.MEDIA_CONTROLLER_ID] = it }
        }
    }

    /**
     * Reconcile the root's controller children after a structural change outside
     * a root re-render (e.g. a nested branch adding a `VIDEO`) or after an
     * out-of-render register/unregister.
     */
    private fun syncRootControllers(root: PathlandNode) {
        val ops = ArrayList<Runnable>()
        val added = ArrayList<PathlandNode>()
        val iterator = root.children.iterator()
        while (iterator.hasNext()) {
            val node = iterator.next()
            if (!isControllerNode(node)) continue
            val controller = node.mediaControllerRef!!
            if (registeredMediaControllers.contains(controller)) continue
            iterator.remove()
            val id = node.id
            val rootId = root.id
            ops.add(Runnable {
                sink.removeChild(rootId, id)
                sink.deleteNode(id)
            })
            destroyNodeEffects(node)
        }
        for (controller in registeredMediaControllers) {
            if (root.children.any { isControllerNode(it) && it.mediaControllerRef === controller }) continue
            val existing = mediaControllers[controller]
            val node = existing ?: newControllerNode(controller).also { mediaControllers[controller] = it }
            if (existing != null) {
                node.propertyBindings.clear()
                applyController(node, controller)
            }
            val index = root.children.size
            root.children.add(node)
            node.parent = root
            node.index = index
            emitSubtreeInto(ops, node, root.id, index)
            added.add(node)
        }
        if (ops.isNotEmpty()) {
            sink.beginFrame()
            for (op in ops) op.run()
            sink.endFrame()
        }
        val slot = rootSlot
        if (slot != null) {
            for (node in added) {
                registerNodeBindings(slot, node)
                collectInputsForNode(node, null)
            }
        }
    }

    private fun destroyNodeEffects(node: PathlandNode) {
        nodeBindings.remove(node.id)?.let { effects ->
            for (effect in effects) {
                effect.destroy()
                bindings.remove(effect)
            }
        }
        forgetInputsForNode(node)
    }

    private fun applyController(node: PathlandNode, c: MediaController) {
        node.properties[Properties.MEDIA_SOURCE] = c.sourceSignal().get()
        node.propertyBindings[Properties.MEDIA_SOURCE] = c.sourceSignal()
        node.properties[Properties.PLAYBACK_STATE] = if (c.playing().get()) 1 else 0
        node.propertyBindings[Properties.PLAYBACK_STATE] = c.playing()
        node.properties[Properties.MEDIA_POSITION] = c.position().get()
        node.propertyBindings[Properties.MEDIA_POSITION] = c.position()
        node.properties[Properties.MEDIA_VOLUME] = c.volume().get()
        node.propertyBindings[Properties.MEDIA_VOLUME] = c.volume()
        node.properties[Properties.MEDIA_MUTED] = if (c.muted().get()) 1 else 0
        node.propertyBindings[Properties.MEDIA_MUTED] = c.muted()
        node.properties[Properties.MEDIA_LOOP] = if (c.loop().get()) 1 else 0
        node.propertyBindings[Properties.MEDIA_LOOP] = c.loop()
        node.properties[Properties.MEDIA_RATE] = c.rate().get()
        node.propertyBindings[Properties.MEDIA_RATE] = c.rate()
        node.mediaInput = object : MediaInput {
            override fun onPlayStateChanged(playing: Boolean) = c.playingReport().set(playing)
            override fun onTimeUpdated(seconds: Float) = c.time().set(seconds)
            override fun onDurationChanged(seconds: Float) = c.duration().set(seconds)
            override fun onEnded() = c.onEnded().run()
            override fun onVolumeChanged(volume: Float) = c.volumeReport().set(volume)
        }
    }

    private fun emitNode(node: PathlandNode) {
        emitNode(node, null, -1)
    }

    private fun emitNode(node: PathlandNode, parentId: Int?, index: Int) {
        sink.createNode(node.id, node.component)
        if (parentId != null) sink.insertChild(parentId, node.id, index)
        ensureBindingId(node)
        for ((property, value) in node.properties) emitProperty(node.id, property, value)
        val attributed = resolvedAttributed(node)
        val rich = attributed?.takeUnless { it.isPlain }
        if (rich != null) {
            sink.setAttributedText(node.id, rich)
        } else {
            val text = attributed?.text ?: node.textValue()
            if (text != null && text.isNotEmpty()) sink.setText(node.id, text)
        }
        if (node.dateBinding != null) sink.setDate(node.id, node.days, node.millisOfDay)
        var childIndex = 0
        for (child in node.children) emitNode(child, node.id, childIndex++)
    }

    private fun ringSink(): Boolean = sink.requiresBindingId()

    private fun ensureBindingId(node: PathlandNode) {
        if (ringSink() && (node.textInput != null || node.valueInput != null || node.dateInput != null)) {
            node.properties.computeIfAbsent(Properties.BINDING_ID) { node.id }
        }
    }

    private fun emitProperty(nodeId: Int, property: Int, value: Any?) {
        val token = value as? TokenPath
        val valueType = if (token?.tokenPath() != null) ValueTypes.DESIGN_TOKEN
        else ValueTypes.forProperty(property)
        sink.setProperty(nodeId, property, valueType, value!!)
    }

    private fun propertyValuesEqual(a: Any?, b: Any?): Boolean {
        if (a is FloatArray && b is FloatArray) return a.contentEquals(b)
        return Objects.equals(a, b)
    }

    private fun isConstant(signal: Signal<*>): Boolean = signal is ConstantSignal<*>

    private fun trackBinding(slot: ViewSlot, nodeId: Int, effect: EffectRef) {
        slot.bindings.add(effect)
        bindings.add(effect)
        nodeBindings.computeIfAbsent(nodeId) { ArrayList() }.add(effect)
    }

    private fun registerNodeBindings(slot: ViewSlot, node: PathlandNode) {
        node.textBinding?.let { textBinding ->
            if (!isConstant(textBinding)) {
                val effect = Signals.effect {
                    val value = textBinding.get()
                    if (value != node.text) {
                        node.text = value
                        val attributed = if (node.markdownText) Markdown.parseOrPlain(value) else null
                        sink.beginFrame()
                        if (attributed != null && !attributed.isPlain) {
                            sink.setAttributedText(node.id, attributed)
                        } else {
                            sink.setText(node.id, attributed?.text ?: value)
                        }
                        sink.endFrame()
                    }
                }
                trackBinding(slot, node.id, effect)
            }
        }
        node.attributedBinding?.let { attributedBinding ->
            if (!isConstant(attributedBinding)) {
                val effect = Signals.effect {
                    val value = attributedBinding.get()
                    if (value != node.attributedText) {
                        node.attributedText = value
                        node.text = value.text
                        sink.beginFrame()
                        if (value.isPlain) {
                            sink.setText(node.id, value.text)
                        } else {
                            sink.setAttributedText(node.id, value)
                        }
                        sink.endFrame()
                    }
                }
                trackBinding(slot, node.id, effect)
            }
        }
        node.dateBinding?.let { dateBinding ->
            if (!isConstant(dateBinding)) {
                val effect = Signals.effect {
                    val days = dateBinding.get()
                    if (days != node.days) {
                        node.days = days
                        sink.beginFrame()
                        sink.setDate(node.id, days, node.millisOfDay)
                        sink.endFrame()
                    }
                }
                trackBinding(slot, node.id, effect)
            }
        }
        for ((property, signal) in node.propertyBindings) {
            if (isConstant(signal)) continue
            val effect = Signals.effect {
                val value = ReactiveValues.from(property, signal)
                if (value != node.properties[property]) {
                    node.properties[property] = value
                    sink.beginFrame()
                    emitProperty(node.id, property, value)
                    sink.endFrame()
                }
            }
            trackBinding(slot, node.id, effect)
        }
        val activePath = node.activePath
        if (activePath != null && node.pathChangeListeners.isNotEmpty()) {
            val listeners = node.pathChangeListeners.toList()
            val effect = Signals.effect({
                val path = activePath.get()
                for (listener in listeners) listener.accept(path)
            }, EffectOptions.allowWrites())
            trackBinding(slot, node.id, effect)
        }
    }

    private fun reconcileChildren(
        parent: PathlandNode,
        oldList: List<PathlandNode>,
        newList: List<PathlandNode>,
        ops: MutableList<Runnable>,
    ) {
        val n = maxOf(oldList.size, newList.size)
        for (i in 0 until n) {
            val old = if (i < oldList.size) oldList[i] else null
            val fresh = if (i < newList.size) newList[i] else null
            if (old != null && fresh != null && old.component == fresh.component) {
                fresh.id = old.id
                reconcileNode(old, fresh, ops)
            } else {
                if (old != null) {
                    val parentId = parent.id
                    val oldId = old.id
                    ops.add(Runnable {
                        sink.removeChild(parentId, oldId)
                        sink.deleteNode(oldId)
                    })
                }
                if (fresh != null) {
                    if (fresh.id == 0) fresh.id = freshIdFor(fresh)
                    emitSubtreeInto(ops, fresh, parent.id, i)
                }
            }
        }
    }

    private fun reconcileNode(old: PathlandNode, fresh: PathlandNode, ops: MutableList<Runnable>) {
        ensureBindingId(fresh)
        for ((property, value) in fresh.properties) {
            if (!propertyValuesEqual(value, old.properties[property])) {
                val id = fresh.id
                ops.add(Runnable { emitProperty(id, property, value) })
            }
        }
        val oldText = old.text
        val freshText = currentText(fresh)
        val oldAttributed = old.attributedText
        var freshAttributed = currentAttributed(fresh)
        if (freshAttributed == null && fresh.markdownText &&
            (oldText != freshText || !old.markdownText)
        ) {
            freshAttributed = freshText?.let { Markdown.parseOrPlain(it) }
        }
        val freshRich = freshAttributed?.takeUnless { it.isPlain }
        if (oldAttributed != freshAttributed) {
            val id = fresh.id
            val rich = freshRich
            if (rich != null) {
                ops.add(Runnable { sink.setAttributedText(id, rich) })
            } else {
                val text = freshAttributed?.text ?: freshText
                if (text != null && text.isNotEmpty()) {
                    ops.add(Runnable { sink.setText(id, text) })
                }
            }
        } else if (freshAttributed == null && oldText != freshText && freshText != null) {
            val id = fresh.id
            ops.add(Runnable { sink.setText(id, freshText) })
        }
        if (old.days != fresh.days || old.millisOfDay != fresh.millisOfDay) {
            val id = fresh.id
            val days = fresh.days
            val millis = fresh.millisOfDay
            ops.add(Runnable { sink.setDate(id, days, millis) })
        }
        reconcileChildren(fresh, old.children, fresh.children, ops)
    }

    private fun emitSubtreeInto(ops: MutableList<Runnable>, node: PathlandNode, parentId: Int, index: Int) {
        ensureBindingId(node)
        val id = node.id
        val component = node.component
        ops.add(Runnable { sink.createNode(id, component) })
        ops.add(Runnable { sink.insertChild(parentId, id, index) })
        for ((property, value) in node.properties) {
            ops.add(Runnable { emitProperty(id, property, value) })
        }
        val attributed = resolvedAttributed(node)
        val rich = attributed?.takeUnless { it.isPlain }
        if (rich != null) {
            ops.add(Runnable { sink.setAttributedText(id, rich) })
        } else {
            val text = attributed?.text ?: currentText(node)
            if (text != null && text.isNotEmpty()) {
                ops.add(Runnable { sink.setText(id, text) })
            }
        }
        if (node.dateBinding != null) {
            val days = node.days
            val millis = node.millisOfDay
            ops.add(Runnable { sink.setDate(id, days, millis) })
        }
        var childIndex = 0
        for (child in node.children) {
            child.id = freshIdFor(child)
            emitSubtreeInto(ops, child, id, childIndex++)
        }
    }

    private fun currentText(node: PathlandNode): String? =
        node.textBinding?.let { Signals.untracked { it.get() } } ?: node.text

    private fun currentAttributed(node: PathlandNode): dev.pathland.view.core.AttributedString? =
        node.attributedBinding?.let { Signals.untracked { it.get() } } ?: node.attributedText

    /** Explicit attributed text, or inline Markdown parsed from the node's text. */
    private fun resolvedAttributed(
        node: PathlandNode,
    ): dev.pathland.view.core.AttributedString? {
        currentAttributed(node)?.let { return it }
        if (!node.markdownText) return null
        return currentText(node)?.let { Markdown.parseOrPlain(it) }
    }

    companion object {
        private val CURRENT = ThreadLocal<Emitter>()

        /** The emitter driving the current render on this thread, or null. */
        fun current(): Emitter? = CURRENT.get()

        /** The ids that occur more than once in [root]'s subtree, in pre-order. */
        fun duplicateIds(root: PathlandNode): List<Int> {
            val seen = LinkedHashSet<Int>()
            val duplicates = LinkedHashSet<Int>()
            collectDuplicateIds(root, seen, duplicates)
            return ArrayList(duplicates)
        }

        private fun collectDuplicateIds(
            node: PathlandNode,
            seen: MutableSet<Int>,
            duplicates: MutableSet<Int>,
        ) {
            if (!seen.add(node.id)) duplicates.add(node.id)
            for (child in node.children) collectDuplicateIds(child, seen, duplicates)
        }
    }
}
