package com.pathland.view;

import com.pathland.view.signal.Signal;

import java.util.function.Consumer;

/**
 * Scopes an {@link EnvironmentKey} → value binding down a wrapped subtree
 * ({@code .modifiers(EnvironmentBinding.with(e -> e.key(...).value(...)))}): the value
 * is active only while that subtree renders, and an outer binding is restored
 * afterward — nearest wins (spec DSL.md §5.5). Read it with
 * {@code Environment.value(key)}, which **always returns a signal**: a plain value is
 * wrapped in a constant signal; a signal value is returned as-is and stays reactive.
 * The modifier value is applied with {@code .modifiers(...)} like any other.
 */
public final class EnvironmentBinding implements ViewModifier {

    /** {@link EnvironmentBinding} values. */
    public static final class Config {

        private EnvironmentKey<?> key;
        private Object value;

        /** The environment key to bind. */
        public Config key(EnvironmentKey<?> key) {
            this.key = key;
            return this;
        }

        /** Bind a plain value (read back as a constant signal). */
        public Config value(Object value) {
            this.value = value;
            return this;
        }

        /** Bind a reactive value (the signal is returned as-is by {@link Environment#value}). */
        public Config value(Signal<?> value) {
            this.value = value;
            return this;
        }
    }

    private final EnvironmentKey<?> key;
    private final Object value;

    private EnvironmentBinding(Config config) {
        this.key = config.key;
        this.value = config.value;
    }

    /** Configure the binding ({@code EnvironmentBinding.with(e -> e.key(...).value(...))}). */
    public static EnvironmentBinding with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new EnvironmentBinding(config);
    }

    @SuppressWarnings("unchecked")
    @Override
    public View body(View content) {
        return new EnvironmentView(content, (EnvironmentKey<Object>) key, value);
    }
}