package dev.pathland.view;

import dev.pathland.view.signal.ConstantSignal;
import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The SwiftUI-like {@code frame} sizing modifier — a {@link ViewModifier} value
 * applied with {@code .modifiers(frame(f -> …))}: a fixed {@code width}/
 * {@code height} ({@code ±∞} normalized to {@code Commands.Size.FILL}), an
 * {@code ALIGNMENT}, and the min/ideal/max bounds. Every value member is
 * signal-capable (spec DSL.md §2).
 */
public final class Frame implements ViewModifier {

    /** {@link Frame} values. */
    public static final class Config {

        private Signal<Float> width;
        private Signal<Float> height;
        private Length widthToken;
        private Length heightToken;
        private Signal<Alignment> alignment;
        private final Signal<Float>[] bounds = newBounds();

        /** A fixed {@code width} (finite; {@code ±∞} = {@code FILL}). */
        public Config width(float value) {
            this.width = rawSize(value);
            this.widthToken = null;
            return this;
        }

        /** Bind a fixed {@code width} to a signal. */
        public Config width(Signal<Float> value) {
            this.width = value;
            this.widthToken = null;
            return this;
        }

        /** Reference a width design token (e.g. {@code size.control.md}). */
        public Config width(Length value) {
            this.widthToken = value;
            this.width = null;
            return this;
        }

        /** A fixed {@code height} (finite; {@code ±∞} = {@code FILL}). */
        public Config height(float value) {
            this.height = rawSize(value);
            this.heightToken = null;
            return this;
        }

        /** Bind a fixed {@code height} to a signal. */
        public Config height(Signal<Float> value) {
            this.height = value;
            this.heightToken = null;
            return this;
        }

        /** Reference a height design token (e.g. {@code size.control.md}). */
        public Config height(Length value) {
            this.heightToken = value;
            this.height = null;
            return this;
        }

        public Config minWidth(float value) {
            return bound(0, value);
        }

        public Config minWidth(Signal<Float> value) {
            bounds[0] = value;
            return this;
        }

        public Config idealWidth(float value) {
            return bound(1, value);
        }

        public Config idealWidth(Signal<Float> value) {
            bounds[1] = value;
            return this;
        }

        public Config maxWidth(float value) {
            return bound(2, value);
        }

        public Config maxWidth(Signal<Float> value) {
            bounds[2] = value;
            return this;
        }

        public Config minHeight(float value) {
            return bound(3, value);
        }

        public Config minHeight(Signal<Float> value) {
            bounds[3] = value;
            return this;
        }

        public Config idealHeight(float value) {
            return bound(4, value);
        }

        public Config idealHeight(Signal<Float> value) {
            bounds[4] = value;
            return this;
        }

        public Config maxHeight(float value) {
            return bound(5, value);
        }

        public Config maxHeight(Signal<Float> value) {
            bounds[5] = value;
            return this;
        }

        /** Position the content inside the resulting box. */
        public Config alignment(Alignment value) {
            this.alignment = Signals.constant(value);
            return this;
        }

        /** Bind the content alignment to a signal. */
        public Config alignment(Signal<Alignment> value) {
            this.alignment = value;
            return this;
        }

        private Config bound(int index, float value) {
            // NaN / an infinite bound means "no limit" — omitted.
            if (!Float.isNaN(value) && !Float.isInfinite(value)) {
                bounds[index] = Signals.constant(value);
            }
            return this;
        }
    }

    private static Signal<Float>[] newBounds() {
        @SuppressWarnings("unchecked")
        Signal<Float>[] bounds = new Signal[6];
        return bounds;
    }

    private final Config config;

    private Frame(Config config) {
        this.config = config;
    }

    /** Configure a frame with {@code .modifiers(frame(f -> …))}. */
    static Frame with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Frame(config);
    }

    @Override
    public View body(View content) {
        List<Modified.Prop> props = new ArrayList<>();
        if (config.widthToken != null) {
            props.add(Modified.prop(Properties.WIDTH, config.widthToken));
        } else if (config.width != null && !isOmitted(config.width)) {
            props.add(Modified.prop(Properties.WIDTH, normalized(config.width)));
        }
        if (config.heightToken != null) {
            props.add(Modified.prop(Properties.HEIGHT, config.heightToken));
        } else if (config.height != null && !isOmitted(config.height)) {
            props.add(Modified.prop(Properties.HEIGHT, normalized(config.height)));
        }
        if (config.alignment != null) {
            props.add(Modified.prop(Properties.ALIGNMENT, config.alignment));
        }
        int[] boundProps = {
            Properties.MIN_WIDTH, Properties.IDEAL_WIDTH, Properties.MAX_WIDTH,
            Properties.MIN_HEIGHT, Properties.IDEAL_HEIGHT, Properties.MAX_HEIGHT
        };
        for (int i = 0; i < boundProps.length; i++) {
            if (config.bounds[i] != null) {
                props.add(Modified.prop(boundProps[i], config.bounds[i]));
            }
        }
        return Modified.props(content, props.toArray(new Modified.Prop[0]));
    }

    private static Signal<Float> rawSize(float value) {
        return Float.isNaN(value) ? null : Signals.constant(value);
    }

    private static boolean isOmitted(Signal<Float> size) {
        return size instanceof ConstantSignal && Float.isNaN(size.get());
    }

    /** Normalize an infinite size hint to the FILL sentinel (reactively). */
    private static Signal<Float> normalized(Signal<Float> size) {
        if (size instanceof ConstantSignal) {
            return Signals.constant(fillOr(size.get()));
        }
        return Signals.computed(() -> fillOr(size.get()));
    }

    private static float fillOr(float value) {
        return Float.isInfinite(value) ? Commands.Size.FILL : value;
    }
}
