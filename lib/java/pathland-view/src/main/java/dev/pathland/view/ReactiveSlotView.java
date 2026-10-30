package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;

import java.util.function.Supplier;

/**
 * The component backing {@link View#of(Supplier)}: a Group-backed slot whose tracked
 * render reads the supplier (so its signals subscribe this component) and renders the
 * selected view as a child component slot.
 */
final class ReactiveSlotView implements View {

    private final Supplier<View> content;

    ReactiveSlotView(Supplier<View> content) {
        this.content = content;
    }

    @Override
    public PathlandNode renderNode(Environment env) {
        PathlandNode node = new PathlandNode(Components.VSTACK); // Group-backed slot
        View selected = content.get(); // tracked: a selector read re-renders this component
        if (selected != null) {
            node.children.add(selected.render(env));
        }
        return node;
    }
}
