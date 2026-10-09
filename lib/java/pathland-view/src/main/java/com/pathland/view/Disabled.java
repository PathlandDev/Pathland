package com.pathland.view;

import com.pathland.view.signal.ConstantSignal;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * disables/enables the view ({@code ENABLED}: 1 = interactive).
 */
public final class Disabled implements ViewModifier {

    /** {@link Disabled} values. */
    public static final class Config {

        private Signal<Boolean> disabled;

        /** Set a static disabled state. */
        public Config disabled(boolean disabled) {
            this.disabled = Signals.constant(disabled);
            return this;
        }

        /** Bind the disabled state to a signal. */
        public Config disabled(Signal<Boolean> disabled) {
            this.disabled = disabled;
            return this;
        }
    }

    private final Signal<Boolean> enabled;

    private Disabled(Config config) {
        Signal<Boolean> disabled = config.disabled != null ? config.disabled : Signals.constant(false);
        // ENABLED is the inverse of `disabled`; a constant stays a constant (no binding).
        this.enabled = disabled instanceof ConstantSignal
                ? Signals.constant(!disabled.get())
                : Signals.computed(() -> !disabled.get());
    }

    /** Disable/enable ({@code disabled} = disabled). */
    public static Disabled of(boolean disabled) {
        return new Disabled(new Config().disabled(disabled));
    }

    /** Configure the disabled state. */
    public static Disabled with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Disabled(config);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.ENABLED, enabled));
    }
}
