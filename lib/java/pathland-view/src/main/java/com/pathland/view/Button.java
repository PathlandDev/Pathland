package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

/**
 * An interactive button ({@code Button}). Accepts an arbitrary child view as
 * the label. The control owns the native {@code BUTTON} node and wires the action on
 * it (whole button tappable, including its padding); the active {@link ButtonStyle}
 * (spec DSL.md §5.7) supplies the button's content — the label, decorated — as the
 * button node's child, so a composite label keeps its own layout.
 */
public final class Button implements View {

    private final View label;
    private final Runnable action;

    private Button(String title, Runnable action) {
        this(Text.of(title), action);
    }

    private Button(View label, Runnable action) {
        this.label = label;
        this.action = action;
    }

    /** A button with a plain text title. */
    public static Button of(String title, Runnable action) {
        return new Button(title, action);
    }

    /** A button with an arbitrary child view as its label. */
    public static Button of(View label, Runnable action) {
        return new Button(label, action);
    }

    @Override
    public PathlandNode render(Environment env) {
        // The control owns the native BUTTON shell + the action; the style supplies
        // the content (the decorated label) as the shell's child. The label keeps its
        // own component(s) and layout (Composite Override Mode, PRIMITIVES.md §2).
        PathlandNode node = new PathlandNode(Components.BUTTON);
        ButtonStyle style = env.buttonStyle();
        View content = style.makeBody(new ButtonStyle.Configuration(label));
        if (content != null && content != EmptyContent.INSTANCE) {
            node.children.add(content.render(env));
        }
        Object existing = node.properties.get(Properties.EVENT_LISTENERS);
        int mask = existing instanceof Integer i ? i : 0;
        node.properties.put(
                Properties.EVENT_LISTENERS,
                mask | Commands.Listeners.POINTER_DOWN | Commands.Listeners.POINTER_UP);
        node.tapActions.add(action);
        return node;
    }
}
