package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A composable view. Composite views declare their subtree with
 * {@link #body()}; the library's primitives (leaves, stacks, controls) instead override
 * {@link #renderNode(Environment)} to materialize a {@link PathlandNode}. The interface
 * is open: the library ships the leaf primitives and modifier wrappers, and
 * application code (Quarkus, Spring Boot, desktop) defines its own views by
 * implementing {@code View} directly.
 *
 * <p>{@link #body()} / {@link #renderNode(Environment)} form a tracked render
 * computation: a signal read during them subscribes the owning component, and a
 * later write re-renders that component and diffs its subtree. The render body must
 * be pure/idempotent and must not write signals. Non-signal reads are mount-time
 * only. {@link #render(Environment)} is the emitter entry point and must not be
 * overridden.
 *
 * <p>Modifiers are {@link ViewModifier} values applied with {@link #modifiers(ViewModifier...)}
 * — built-in and application-authored modifiers share one mechanism (spec DSL.md
 * §5.6), applied innermost-first. There is no per-modifier factory sugar on this
 * interface. Constructor (structural/layout) properties are passed to the view
 * constructors and are never chainable.
 */
public interface View {

    /**
     * The base marker for a view's **value config** ({@code V.Config}). The
     * config is populated by the static {@code View.make(Consumer<Config>)}
     * factory and is the same type a style's {@code makeBody(Config)} reads
     * (spec DSL.md §5.7).
     */
    interface Config {
    }

    /**
     * Declarative composition: a composite view returns its subtree here. Primitives
     * return {@code this} (the identity body) and instead override
     * {@link #renderNode(Environment)}.
     */
    default View body() {
        return this;
    }

    /**
     * The emitter entry point: render this view as a tracked component slot. Called by
     * a parent view for each child ({@code child.render(env)}). The emitter installs a
     * {@link dev.pathland.view.emit.Emitter#current() current} render context; direct
     * calls outside an emitter fall back to {@link #renderNode(Environment)}.
     */
    default PathlandNode render(Environment env) {
        dev.pathland.view.emit.Emitter emitter = dev.pathland.view.emit.Emitter.current();
        return emitter == null ? renderNode(env) : emitter.renderView(this, env);
    }

    /**
     * The root-chain render: composite views resolve {@link #body()} and delegate to
     * the resolved view's {@code renderNode} (same component); wrappers delegate to
     * their inner view's {@code renderNode}; primitives build a {@link PathlandNode}
     * and call {@code child.render(env)} for child views (new component slots).
     */
    default PathlandNode renderNode(Environment env) {
        dev.pathland.view.state.PersistentState state = env.state();
        if (state != null) {
            state.connect(this); // wire this view's State fields (idempotent)
        }
        View resolved = body();
        if (resolved != this) {
            return resolved.renderNode(env);
        }
        throw new UnsupportedOperationException(
                getClass().getName() + " must override body() (composite) or renderNode() (primitive)");
    }

    /**
     * Wrap this view in one or more modifiers ({@code .modifier}), applied
     * innermost-first: {@code modifiers(A, B, C)} ≡ {@code modifiers(A).modifiers(B).modifiers(C)}.
     * A single modifier is the common case; an empty call returns {@code this}.
     * {@code with(...)} is for values only — modifiers always go through this
     * {@code modifiers(...)} operation.
     */
    default View modifiers(ViewModifier... modifiers) {
        View result = this;
        for (ViewModifier modifier : modifiers) {
            result = Modified.apply(result, modifier);
        }
        return result;
    }

    /**
     * A reactive subtree from a supplier: the inline form of control flow. The supplier
     * is read during this component's tracked render, so any signal it reads re-renders
     * the slot; the selected view is a child component slot.
     *
     * <pre>{@code
     * VStack.make().children(
     *     View.of(() -> mode.get() == Mode.LIST ? listView : gridView));
     * }</pre>
     */
    static View of(Supplier<View> content) {
        return new ReactiveSlotView(Objects.requireNonNull(content, "content"));
    }
}