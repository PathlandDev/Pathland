package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.function.Consumer;

/**
 * Determinate progress or an activity indicator ({@code ProgressView}).
 * {@code progress} is {@code 0..1}; {@code indeterminate()} builds the animated
 * spinner variant ({@code IS_INDETERMINATE}).
 */
public final class ProgressView implements View, Configurable<ProgressView.Config> {

    /** {@link ProgressView} values. */
    public static final class Config implements View.Config {

        private Float value;

        /** Set the determinate value ({@code 0..1}). */
        public Config value(float value) {
            this.value = value;
            return this;
        }

        /** Build the indeterminate activity-indicator variant. */
        public Config indeterminate() {
            this.value = -1f;
            return this;
        }
    }

    private final Config config;

    private ProgressView(Config config) {
        this.config = config;
    }

    /** A determinate progress bar ({@code value} in {@code 0..1}). */
    public static ProgressView of(float value) {
        return new ProgressView(new Config().value(value));
    }

    /** A determinate progress bar ({@code value} in {@code 0..1}). */
    public static ProgressView progress(float value) {
        return new ProgressView(new Config().value(value));
    }

    /** An indeterminate activity indicator (renderer-animated). */
    public static ProgressView indeterminate() {
        return new ProgressView(new Config().indeterminate());
    }

    /** Configure the progress view's values. */
    public static ViewBuilder<ProgressView, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new ProgressView(config));
    }

    /** Apply modifiers to a bare progress view. */
    public static ViewBuilder<ProgressView, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new ProgressView(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.PROGRESS_VIEW);
        if (config.value == null || config.value < 0f) {
            node.properties.put(Properties.IS_INDETERMINATE, 1);
        } else {
            node.properties.put(Properties.PROGRESS, config.value);
        }
        return node;
    }
}
