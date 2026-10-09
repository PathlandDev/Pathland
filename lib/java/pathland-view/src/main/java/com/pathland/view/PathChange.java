package com.pathland.view;

import java.util.function.Consumer;

/**
 * Registers a listener for the active platform path (spec DSL.md §4.5) — the
 * {@code onOpenURL} idea generalized across platforms. Applied as a modifier value:
 * {@code .modifiers(PathChange.with(p -> p.listener(path -> …)))}. The listener fires
 * whenever the {@link Platform#ACTIVE_PATH} signal changes (including its initial
 * value); a {@code Router}, when present, binds to the same signal.
 */
public final class PathChange implements ViewModifier {

    /** {@link PathChange} values. */
    public static final class Config {

        private Consumer<String> listener;

        /** The listener to fire on every active-path change. */
        public Config listener(Consumer<String> listener) {
            this.listener = listener;
            return this;
        }
    }

    private final Consumer<String> listener;

    private PathChange(Config config) {
        this.listener = config.listener;
    }

    /** Configure the path-change listener ({@code PathChange.with(p -> p.listener(...))}). */
    public static PathChange with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new PathChange(config);
    }

    @Override
    public View body(View content) {
        return new PathChangeView(content, listener);
    }
}