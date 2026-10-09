package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * Determinate progress or an activity indicator ({@code ProgressView}).
 * {@code value} is {@code 0..1}; {@code indeterminate()} builds the animated
 * spinner variant ({@code IS_INDETERMINATE}).
 */
public final class ProgressView implements View, Configurable<ProgressView.Config> {

    /** {@link ProgressView} values. */
    public static final class Config implements View.Config {

        private Signal<Float> value;
        private boolean indeterminate;

        /** Set the determinate value ({@code 0..1}). */
        public Config value(float value) {
            this.value = Signals.constant(value);
            this.indeterminate = false;
            return this;
        }

        /** Bind the determinate value ({@code 0..1}) to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            this.indeterminate = false;
            return this;
        }

        /** Build the indeterminate activity-indicator variant. */
        public Config indeterminate() {
            this.value = null;
            this.indeterminate = true;
            return this;
        }
    }

    private final Config config;

    private ProgressView(Config config) {
        this.config = config;
    }

    /** A determinate progress bar ({@code value} in {@code 0..1}). */
    public static ProgressView of(float value) {
        Config config = new Config();
        if (value < 0f) {
            config.indeterminate();
        } else {
            config.value(value);
        }
        return new ProgressView(config);
    }

    /** A determinate progress bar ({@code value} in {@code 0..1}). */
    public static ProgressView progress(float value) {
        return of(value);
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
        if (config.indeterminate || config.value == null) {
            node.properties.put(Properties.IS_INDETERMINATE, 1);
        } else {
            node.property(Properties.PROGRESS, config.value);
        }
        return node;
    }
}
