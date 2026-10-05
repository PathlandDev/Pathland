package com.pathland.view;

import java.util.function.Consumer;

/**
 * Registers a listener for the active platform path (spec DSL.md §4.5) — the
 * {@code onOpenURL} idea generalized across platforms. Applied via
 * {@code View#onPathChange(Consumer)}. The listener fires whenever the
 * {@link Platform#ACTIVE_PATH} signal changes (including its initial value);
 * a {@code Router}, when present, binds to the same signal.
 */
public final class PathChangeMod implements ViewModifier {

    private final Consumer<String> listener;

    PathChangeMod(Consumer<String> listener) {
        this.listener = listener;
    }

    @Override
    public View body(View content) {
        return new PathChangeView(content, listener);
    }
}