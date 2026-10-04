package com.pathland.view.emit;

import com.pathland.view.transport.Event;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The result of mounting a view tree: the root node id and the event-routing maps. The
 * host forwards raw input events into these maps — it never reaches into view internals.
 *
 * <p>The routing maps are <b>live unmodifiable views</b> of the emitter's maps: a
 * structural reconcile (spec DSL.md §3.4 — a {@code Conditional.when} branch swap, a
 * navigation destination change) adds/removes entries after mount, and a host reading
 * the maps per event sees the current routing.
 *
 * @param rootId          the retained tree's root node id
 * @param tapActions      node id → pointer-up action
 * @param textInputs      node id → text-input sink (TEXT_CHANGED)
 * @param valueInputs     node id → value-input sink (VALUE_CHANGED)
 * @param editingInputs   node id → editing-changed sink (EDITING_CHANGED)
 * @param dateInputs      node id → date-input sink (DATE_CHANGED)
 * @param mediaInputs     node id → media-event sink (MEDIA_*; bound AUDIO/VIDEO)
 * @param navigateActions node id → declarative route-change action (a {@code .navigate/
 *                        push/replace} intent resolved to the nearest enclosing router)
 * @param navigateHandler global sink for {@code NAVIGATE} events (host → guest; a
 *                        {@code NavigationContainer} routes them into its router —
 *                        back/forward/deep-link). Null when no router is mounted.
 */
public record RenderResult(
        int rootId,
        Map<Integer, Runnable> tapActions,
        Map<Integer, Consumer<String>> textInputs,
        Map<Integer, Consumer<Float>> valueInputs,
        Map<Integer, Consumer<Boolean>> editingInputs,
        Map<Integer, DateInput> dateInputs,
        Map<Integer, MediaInput> mediaInputs,
        Map<Integer, Runnable> navigateActions,
        Consumer<Event> navigateHandler) {}