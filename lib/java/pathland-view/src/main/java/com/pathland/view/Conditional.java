package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Factory for **structural reactivity** (spec DSL.md §3.4): a structural container
 * whose single child subtree is selected by a signal and reconciled into `TREE`
 * deltas when the signal changes. This is the DSL surface for `if`/`else` and
 * `switch` — the foundation the router's {@code NavigationContainer} builds on.
 *
 * <pre>{@code
 * import static com.pathland.view.Conditional.when;
 * import static com.pathland.view.Conditional.Case;
 *
 * when(showLogin, LoginView.make(), HomeView.make());          // if / else
 * when(mode,
 *     Case.of(RouteMode.HOME, HomeView.make()),
 *     Case.of(RouteMode.USERS, UsersView.make()),
 *     Case.otherwise(NotFoundView.make()));                    // switch + default
 * }</pre>
 *
 * <p>The names {@code if}, {@code switch}, {@code case}, and {@code else} are Java
 * reserved keywords, so {@code when} (Kotlin's {@code switch} analog) is the method
 * name and {@code Case.of(...)} / {@code Case.otherwise(...)} carry the branches.
 * A boolean signal takes the two-branch overload; an enum/int/string signal takes
 * keyed {@code Case} branches typed to the signal's value type.
 *
 * <p><b>Emission contract:</b> on selector change the emitter re-evaluates the
 * content, diffs the retained subtree, and emits only {@code TREE} deltas; an
 * identical recompute emits **zero** opcodes. The container materializes as a
 * {@code Group} slot (a bare {@code VSTACK} node with no spacing/alignment).
 */
public final class Conditional {

    private Conditional() {}

    /** If/else: {@code then} when the boolean signal is true, else {@code elseView}. */
    public static View when(Signal<Boolean> selector, View then, View elseView) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(then, "then");
        Objects.requireNonNull(elseView, "elseView");
        return new ConditionalWhen(() -> selector.get() ? then : elseView);
    }

    /** Switch: the first {@code Case} whose value equals the signal's value wins; {@code Case.otherwise} is the default. No match without an otherwise → empty slot. */
    @SafeVarargs
    public static <T> View when(Signal<T> selector, Case<T>... cases) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(cases, "cases");
        return new ConditionalWhen(() -> match(selector.get(), cases));
    }

    private static <T> View match(T value, Case<T>[] cases) {
        for (Case<T> c : cases) {
            if (c.otherwise) {
                return c.view;
            }
            if (Objects.equals(c.value, value)) {
                return c.view;
            }
        }
        return null; // no match and no otherwise → the slot is empty
    }

    /**
     * A switch branch: a value-keyed view or the default. {@code Case.of(value, view)}
     * matches when the selector signal equals {@code value}; {@code Case.otherwise(view)}
     * is the default branch. Order matters: {@code otherwise} short-circuits.
     */
    public record Case<T>(T value, View view, boolean otherwise) {

        /** A branch selected when the signal's value equals {@code value}. */
        public static <T> Case<T> of(T value, View view) {
            return new Case<>(value, Objects.requireNonNull(view, "view"), false);
        }

        /** The default branch, selected when no keyed branch matches. */
        public static <T> Case<T> otherwise(View view) {
            return new Case<>(null, Objects.requireNonNull(view, "view"), true);
        }
    }

    /** The structural slot view backing {@link Conditional#when}. */
    static final class ConditionalWhen implements View {

        private final Supplier<View> content;

        ConditionalWhen(Supplier<View> content) {
            this.content = content;
        }

        @Override
        public PathlandNode render(Environment env) {
            // Group-backed slot: a bare VSTACK node with no spacing/alignment (Group.java).
            PathlandNode node = new PathlandNode(Components.VSTACK);
            // Capture the incoming scope (ancestors' environment values — e.g. a router
            // scoped by an enclosing NavigationContainer) so a re-render of this branch
            // re-applies it (Emitter.reconcileSlot).
            node.environmentForChildren = Environment.current();
            View selected = content.get(); // reads the selector; mount is outside any effect
            if (selected != null) {
                node.children.add(selected.render(env));
            }
            node.structuralContent = content;
            return node;
        }
    }
}