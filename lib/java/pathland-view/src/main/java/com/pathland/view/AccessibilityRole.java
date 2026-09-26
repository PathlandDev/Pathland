package com.pathland.view;

/**
 * the accessibility role ({@code ROLE} semantic enum code).
 *
 * <p>The catalog is semantic structure only ({@link Roles}); interactive/control
 * roles are intrinsic to the control components, so passing a code outside the
 * defined semantic set is rejected — a custom button uses {@link Button} +
 * {@link ButtonStyle}.
 */
public final class AccessibilityRole implements ViewModifier {

    private final float value;

    private AccessibilityRole(float value) {
        this.value = value;
    }

    /**
     * A semantic role (see {@link Roles}). Throws if {@code value} is not a
     * defined semantic role — control roles (button, link, checkbox, …) cannot
     * be specified: they are conveyed by the control components themselves.
     */
    public static AccessibilityRole of(int value) {
        if (!Roles.isDefined(value)) {
            throw new IllegalArgumentException(
                    "not a semantic role: " + value
                            + " (ROLE is semantic structure only; interactive/control roles "
                            + "are intrinsic to the control components — a custom button uses "
                            + "Button + ButtonStyle)");
        }
        return new AccessibilityRole((float) value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.ROLE, value));
    }
}