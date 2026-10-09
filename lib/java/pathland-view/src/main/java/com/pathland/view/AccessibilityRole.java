package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * the accessibility role ({@code ROLE} semantic enum code).
 *
 * <p>The catalog is semantic structure only ({@link Roles}); interactive/control
 * roles are intrinsic to the control components, so a static code outside the
 * defined semantic set is rejected. A reactive role is validated by the renderer.
 */
public final class AccessibilityRole implements ViewModifier {

    /** {@link AccessibilityRole} values. */
    public static final class Config {

        private Signal<Integer> role;

        /** Set a static semantic role code (see {@link Roles}). */
        public Config role(int role) {
            this.role = Signals.constant(role);
            return this;
        }

        /** Bind the semantic role code to a signal. */
        public Config role(Signal<Integer> role) {
            this.role = role;
            return this;
        }
    }

    private final Config config;

    private AccessibilityRole(Config config) {
        this.config = config;
    }

    /**
     * A semantic role (see {@link Roles}). Throws if {@code value} is not a
     * defined semantic role.
     */
    public static AccessibilityRole of(int value) {
        if (!Roles.isDefined(value)) {
            throw new IllegalArgumentException(
                    "not a semantic role: " + value + " (ROLE is semantic structure only)");
        }
        return new AccessibilityRole(new Config().role(value));
    }

    /** Configure the accessibility role. */
    public static AccessibilityRole with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new AccessibilityRole(config);
    }

    @Override
    public View body(View content) {
        Signal<Integer> role = config.role != null ? config.role : Signals.constant(0);
        return Modified.props(content, Modified.prop(Properties.ROLE, role));
    }
}
