package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;
import java.util.function.Consumer;

/**
 * An interactive button ({@code Button}). Accepts an arbitrary child view as
 * the label. The control owns the native {@code BUTTON} node and wires the action on
 * it (whole button tappable, including its padding); the active {@link ButtonStyle}
 * (spec DSL.md §5.7) supplies the button's content — the label, decorated — as the
 * button node's child, so a composite label keeps its own layout.
 *
 * <p>Content precedence (spec DSL.md §4): a child supplied via {@code .children(…)}
 * wins, then a {@code title} value, then the style's own content.
 */
public final class Button implements View, Configurable<Button.Config>, ChildrenView {

    /** {@link Button} values. */
    public static final class Config implements View.Config {

        private Runnable action;
        private String title;

        /** Set the tap action. */
        public Config action(Runnable action) {
            this.action = action;
            return this;
        }

        /** Set a plain text title (used when no child is supplied). */
        public Config title(String title) {
            this.title = title;
            return this;
        }
    }

    private final Config config;
    private List<View> children = List.of();

    private Button(Config config, List<View> children) {
        this.config = config;
        this.children = children;
    }

    /** A button with a plain text title. */
    public static Button of(String title, Runnable action) {
        return new Button(new Config().title(title).action(action), List.of());
    }

    /** A button with an arbitrary child view as its label. */
    public static Button of(View label, Runnable action) {
        return new Button(new Config().action(action), List.of(label));
    }

    /** Configure the button's values. */
    public static ViewBuilder<Button, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Button(config, List.of()));
    }

    /** Supply the button's label. */
    public static ViewBuilder<Button, Config> children(View... children) {
        return new ViewBuilder<>(new Button(new Config(), List.of())).children(children);
    }

    /** Apply modifiers to an action-less button. */
    public static ViewBuilder<Button, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Button(new Config(), List.of())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public void setChildren(List<View> children) {
        this.children = List.copyOf(children);
    }

    @Override
    public PathlandNode render(Environment env) {
        // The control owns the native BUTTON shell + the action; the style supplies
        // the content (the decorated label) as the shell's child. The label keeps its
        // own component(s) and layout (Composite Override Mode, PRIMITIVES.md §2).
        View label = !children.isEmpty()
                ? children.get(0)
                : (config.title != null ? Text.of(config.title) : null);

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
        node.tapActions.add(config.action);
        return node;
    }
}
