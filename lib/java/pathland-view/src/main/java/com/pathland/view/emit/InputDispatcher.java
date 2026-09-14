package com.pathland.view.emit;

import com.pathland.view.signal.WritableSignal;
import com.pathland.view.transport.Event;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Routes raw input events (host → guest) into the app's bindings via the routing
 * maps a {@link RenderResult} exposes — never reaching into a view's internals.
 *
 * <p>Transport-agnostic: the server session (WebSocket event batches) and the
 * native GTK host (shared-ring events) feed the same dispatcher. {@code activePath}
 * is the host's {@code Platform.ACTIVE_PATH} signal, updated on {@code NAVIGATE}
 * URL events so a bound Router re-routes guard-aware.
 */
public final class InputDispatcher {

    private final Map<Integer, Runnable> tapActions;
    private final Map<Integer, Runnable> navigateActions;
    private final Map<Integer, Consumer<String>> textInputs;
    private final Map<Integer, Consumer<Float>> valueInputs;
    private final Map<Integer, DateInput> dateInputs;
    private final Consumer<Event> navigateHandler;
    private final WritableSignal<String> activePath;

    public InputDispatcher(RenderResult result, WritableSignal<String> activePath) {
        this.tapActions = result.tapActions();
        this.navigateActions = result.navigateActions();
        this.textInputs = result.textInputs();
        this.valueInputs = result.valueInputs();
        this.dateInputs = result.dateInputs();
        this.navigateHandler = result.navigateHandler();
        this.activePath = activePath;
    }

    /** Dispatch every event in a decoded batch. */
    public void dispatch(Iterable<Event> events) {
        for (Event event : events) {
            dispatch(event);
        }
    }

    /** Route a single raw input event into the app's bindings. */
    public void dispatch(Event event) {
        if (event.isPointerUp()) {
            // A declared navigation intent (`.navigate/.push/.replace`) wins over a
            // plain tap action — route it first (spec DSL.md §4.5).
            Runnable nav = navigateActions.get(event.target());
            if (nav != null) {
                nav.run();
            } else {
                Runnable action = tapActions.get(event.target());
                if (action != null) {
                    action.run();
                }
            }
        } else if (event.isTextChanged()) {
            Consumer<String> sink = textInputs.get(event.target());
            if (sink != null) {
                sink.accept(event.text());
            }
        } else if (event.isValueChanged()) {
            Consumer<Float> sink = valueInputs.get(event.target());
            if (sink != null) {
                sink.accept(event.value());
            }
        } else if (event.isDateChanged()) {
            DateInput sink = dateInputs.get(event.target());
            if (sink != null) {
                sink.accept(event.days(), event.millisOfDay());
            }
        } else if (event.isNavigate()) {
            if (event.url() != null) {
                // A URL (deep link / popstate): update the active path — a bound
                // Router re-routes guard-aware, and any onPathChange listener fires.
                activePath.set(event.url());
            } else {
                // "Back one step" has no path — only meaningful with a Router.
                Consumer<Event> sink = navigateHandler;
                if (sink != null) {
                    sink.accept(event);
                }
            }
        }
    }
}