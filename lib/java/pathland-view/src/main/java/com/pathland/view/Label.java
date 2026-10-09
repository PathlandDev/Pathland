package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * A text title with an optional icon ({@code Label}). The label's parts are
 * handed to the active {@link LabelStyle}, which decides which render and how they are
 * arranged (spec DSL.md §5.7) — the default {@link DefaultLabelStyle} shows an
 * {@link HStack} of an optional {@link Image} and an optional {@link Text}. The title
 * is <em>always</em> the label's accessibility label, even when only the icon is shown
 * (so an icon-only label stays announced by screen readers). A blank title or icon is
 * suppressed (the part is passed as {@code null}).
 */
public final class Label implements View, Configurable<Label.Config> {

    /** {@link Label} values. */
    public static final class Config implements View.Config {

        private Signal<String> title;
        private Signal<String> icon;
        private View iconView;

        /** Set a static title. */
        public Config title(String title) {
            this.title = Signals.constant(title);
            return this;
        }

        /** Bind the title to a reactive signal. */
        public Config title(Signal<String> title) {
            this.title = title;
            return this;
        }

        /** Set a static icon (by image name). */
        public Config icon(String icon) {
            this.icon = Signals.constant(icon);
            return this;
        }

        /** Bind the icon to a reactive signal. */
        public Config icon(Signal<String> icon) {
            this.icon = icon;
            return this;
        }

        /** Set a semantic {@link Icon}. */
        public Config icon(Icon icon) {
            this.iconView = icon;
            return this;
        }
    }

    private final Config config;

    private Label(Config config) {
        this.config = config;
    }

    /** A title-only label (no icon). */
    public static Label of(String title) {
        return new Label(new Config().title(title));
    }

    /** A label with a title and an icon. */
    public static Label of(String title, String icon) {
        return new Label(new Config().title(title).icon(icon));
    }

    /** A label with a title and a semantic {@link Icon}. */
    public static Label of(String title, Icon icon) {
        return new Label(new Config().title(title).icon(icon));
    }

    /** A label whose title is bound to a signal, with a semantic {@link Icon}. */
    public static Label of(Signal<String> title, Icon icon) {
        return new Label(new Config().title(title).icon(icon));
    }

    /** A title-only label whose title is bound to a reactive signal. */
    public static Label of(Signal<String> title) {
        return new Label(new Config().title(title));
    }

    /** A label with a reactive title and a static icon. */
    public static Label of(Signal<String> title, String icon) {
        return new Label(new Config().title(title).icon(icon));
    }

    /** A label with a static title and a reactive icon. */
    public static Label of(String title, Signal<String> icon) {
        return new Label(new Config().title(title).icon(icon));
    }

    /** A label whose title and icon are both bound to reactive signals. */
    public static Label of(Signal<String> title, Signal<String> icon) {
        return new Label(new Config().title(title).icon(icon));
    }

    /** Configure the label's values. */
    public static ViewBuilder<Label, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Label(config));
    }

    /** Apply modifiers to a bare label. */
    public static ViewBuilder<Label, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Label(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public View body() {
        LabelStyle style = Environment.value(Environment.LABEL_STYLE).get();
        if (style == null) {
            style = DefaultLabelStyle.INSTANCE;
        }
        String title = config.title != null ? config.title.get() : null;
        String icon = config.icon != null ? config.icon.get() : null;

        View titleView = nonBlank(title) ? Text.of(config.title).with(LineLimit.of(1)) : null;
        View iconPart = config.iconView != null
                ? config.iconView
                : (nonBlank(icon) ? Image.of(config.icon) : null);

        View content = style.makeBody(new LabelStyle.Configuration(titleView, iconPart));
        if (content == null) {
            content = Group.of();
        }
        if (nonBlank(title)) {
            content = content.with(AccessibilityLabel.of(config.title));
        }
        return content;
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
