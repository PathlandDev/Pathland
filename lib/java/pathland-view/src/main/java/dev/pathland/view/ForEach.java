package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.Signal;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * A reactive list component: maps a list (or a {@code Signal<List<T>>}) to child
 * component slots. The callback receives the item and its index.
 *
 * <pre>{@code
 * VStack.make().children(
 *     ForEach.of(tracks, (track, index) -> TrackRowView.make(r -> r
 *             .track(track)
 *             .index(index))));
 * }</pre>
 *
 * <p>The component materializes as a Group (a bare {@code VSTACK} node) and each
 * item is rendered as a child component slot. The static overload maps once per
 * render; the {@link Signal} overload reads the list during the tracked render, so
 * a list change re-renders only this component. Reconciliation is position + type
 * (no keys): appends/removes are cheap; reorders churn.
 */
public final class ForEach {

    private ForEach() {}

    /** A static list, mapped once per render. */
    public static <T> View of(List<T> items, BiFunction<T, Integer, View> content) {
        Objects.requireNonNull(items, "items");
        return new ForEachView<>(() -> items, content);
    }

    /** A reactive list; a change re-renders only this component. */
    public static <T> View of(Signal<List<T>> items, BiFunction<T, Integer, View> content) {
        Objects.requireNonNull(items, "items");
        return new ForEachView<>(items::get, content);
    }

    private static final class ForEachView<T> implements View {

        private final Supplier<List<T>> source;
        private final BiFunction<T, Integer, View> content;

        ForEachView(Supplier<List<T>> source, BiFunction<T, Integer, View> content) {
            this.source = source;
            this.content = Objects.requireNonNull(content, "content");
        }

        @Override
        public PathlandNode renderNode(Environment env) {
            PathlandNode node = new PathlandNode(Components.VSTACK); // Group-backed slot
            List<T> items = source.get(); // tracked for the Signal overload
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    View child = content.apply(items.get(i), i);
                    if (child != null) {
                        node.children.add(child.render(env));
                    }
                }
            }
            return node;
        }
    }
}
