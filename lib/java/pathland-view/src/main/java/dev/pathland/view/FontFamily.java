package dev.pathland.view;

import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * font family (a {@code STRING} property).
 */
public final class FontFamily implements ViewModifier {

    /** A font-family **design-token reference** (e.g. {@code font.body.family}). */
    public record Token(String token) implements TokenPath {
        @Override
        public String tokenPath() {
            return token;
        }
    }

    /** {@link FontFamily} values. */
    public static final class Config {

        private Signal<String> value;
        private Token token;

        /** Set a static font family name. */
        public Config family(String value) {
            this.value = Signals.constant(value);
            this.token = null;
            return this;
        }

        /** Bind the font family name to a signal. */
        public Config family(Signal<String> value) {
            this.value = value;
            this.token = null;
            return this;
        }

        /** Reference a font-family design token (e.g. {@code font.body.family}). */
        public Config family(Token token) {
            this.token = token;
            this.value = null;
            return this;
        }
    }

    private final Config config;

    private FontFamily(Config config) {
        this.config = config;
    }

    static FontFamily with(String value) {
        return Configurators.modifier(Config::new, FontFamily::new, c -> c.family(value));
    }

    static FontFamily with(Signal<String> value) {
        return Configurators.modifier(Config::new, FontFamily::new, c -> c.family(value));
    }

    static FontFamily with(Token token) {
        return Configurators.modifier(Config::new, FontFamily::new, c -> c.family(token));
    }

    /** Configure the font family. */
    static FontFamily with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new FontFamily(config);
    }

    @Override
    public View body(View content) {
        if (config.token != null) {
            return Modified.props(content, Modified.prop(Properties.FONT_FAMILY, config.token));
        }
        Signal<String> value = config.value != null ? config.value : Signals.constant("");
        return Modified.props(content, Modified.prop(Properties.FONT_FAMILY, value));
    }
}
