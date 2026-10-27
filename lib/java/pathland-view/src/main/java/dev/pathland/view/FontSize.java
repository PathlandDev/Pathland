package dev.pathland.view;

import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * text font size in points ({@code FONT_SIZE}); a single {@link Signal<Float>} —
 * a static size is sugar for a constant signal.
 */
public final class FontSize implements ViewModifier {

    /** {@link FontSize} values. */
    public static final class Config {

        private Signal<Float> signal;
        private Length token;

        /** Set a static size. */
        public Config size(float size) {
            this.signal = Signals.constant(size);
            this.token = null;
            return this;
        }

        /** Bind the size to a signal. */
        public Config size(Signal<Float> signal) {
            this.signal = signal;
            this.token = null;
            return this;
        }

        /** Reference a font-size design token (e.g. {@code font.body.size}). */
        public Config size(Length token) {
            this.token = token;
            this.signal = null;
            return this;
        }
    }

    private final Signal<Float> signal;
    private final Length token;

    private FontSize(Config config) {
        this.signal = config.signal;
        this.token = config.token;
    }

    static FontSize with(float size) {
        return Configurators.modifier(Config::new, FontSize::new, c -> c.size(size));
    }

    static FontSize with(Signal<Float> signal) {
        return Configurators.modifier(Config::new, FontSize::new, c -> c.size(signal));
    }

    static FontSize with(Length token) {
        return Configurators.modifier(Config::new, FontSize::new, c -> c.size(token));
    }

    /** Configure the font size. */
    static FontSize with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new FontSize(config);
    }

    @Override
    public View body(View content) {
        if (token != null) {
            return Modified.props(content, Modified.prop(Properties.FONT_SIZE, token));
        }
        return Modified.props(content, Modified.prop(Properties.FONT_SIZE, signal));
    }
}
