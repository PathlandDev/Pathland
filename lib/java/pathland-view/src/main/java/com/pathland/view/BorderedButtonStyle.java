package com.pathland.view;

import java.util.function.Consumer;

/**
 * A bordered, tinted button style: the label padded onto a colored background with a
 * rounded border. The {@link Button} control wires the action on the native button
 * node, so this style only decorates the content.
 *
 * <p>Configured via {@code BorderedButtonStyle.with(b -> b.padding(...).background(...)...)};
 * {@link #INSTANCE} is the default. A private constructor keeps creation on the
 * configurator (spec DSL.md §2).
 */
public final class BorderedButtonStyle implements ButtonStyle {

    public static final BorderedButtonStyle INSTANCE = new BorderedButtonStyle(new Config());

    /** {@link BorderedButtonStyle} values. */
    public static final class Config implements View.Config {

        private float padding = 10f;
        private Color background = Color.rgb(33, 150, 243);
        private Color borderColor = Color.rgb(25, 118, 210);
        private float radius = 6f;

        /** The label padding. */
        public Config padding(float padding) {
            this.padding = padding;
            return this;
        }

        /** The background color. */
        public Config background(Color background) {
            this.background = background;
            return this;
        }

        /** The border color. */
        public Config borderColor(Color borderColor) {
            this.borderColor = borderColor;
            return this;
        }

        /** The corner radius. */
        public Config radius(float radius) {
            this.radius = radius;
            return this;
        }
    }

    private final Config config;

    private BorderedButtonStyle(Config config) {
        this.config = config;
    }

    /** Configure a bordered style ({@code BorderedButtonStyle.with(b -> …)}). */
    public static BorderedButtonStyle with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new BorderedButtonStyle(config);
    }

    @Override
    public View makeBody(Configuration config) {
        return config.label().modifiers(
                        ForegroundStyle.with(f -> f.color(Color.WHITE)),
                        Padding.with(p -> p.uniform(this.config.padding)),
                        Background.with(b -> b.color(this.config.background)),
                        Border.with(b -> b.color(this.config.borderColor).width(1f).radius(this.config.radius)));
    }
}