package com.pathland.view;

/**
 * A button style ({@code ButtonStyle}). A style turns a button's
 * {@link ButtonStyle.Configuration} (its label) into the button's <em>content</em>
 * view. The {@link Button} control owns the native {@code BUTTON} node and wires the
 * action, so a style only decorates the label (spec DSL.md §5.7). Styles are injected
 * down the tree via {@link View#buttonStyle(ButtonStyle)}.
 */
public interface ButtonStyle extends Style {

    /** Build the styled button content for {@code config}. */
    View makeBody(Configuration config);

    /** Scopes this style down the wrapped subtree ({@code .modifiers(ButtonStyle)}). */
    @Override
    default View body(View content) {
        return EnvironmentView.of(content, Environment.BUTTON_STYLE, this);
    }

    /**
     * The configuration of the button being styled: its label. The {@link Button}
     * control attaches the returned content as the button node's child, and wires the
     * action on the button node. A style therefore never attaches the tap gesture.
     */
    record Configuration(View label) {}
}
