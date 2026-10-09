package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * A semantic icon (the {@code ICON} primitive): a canonical {@link IconName}
 * written once in the UI model and mapped by each renderer to its native icon
 * set. The name is a single {@link Signal<String>}; a change re-emits only this
 * node's {@code ICON_NAME}.
 *
 * <p>Size follows {@code FONT_SIZE} (text-style, like SwiftUI symbol sizing) and
 * tint follows {@code COLOR}/{@code foregroundStyle}; an {@code label} makes the
 * icon presentable to screen readers.
 */
public final class Icon implements View, Configurable<Icon.Config> {

    /** {@link Icon} values. */
    public static final class Config implements View.Config {

        private Signal<String> name;
        private String label;

        /** Set a static canonical icon. */
        public Config name(IconName name) {
            this.name = Signals.constant(name.wire());
            return this;
        }

        /** Set a static canonical icon by canonical name (for names outside the enum). */
        public Config name(String canonicalName) {
            this.name = Signals.constant(canonicalName);
            return this;
        }

        /** Bind the icon to a canonical-name signal. */
        public Config name(Signal<String> canonicalName) {
            this.name = canonicalName;
            return this;
        }

        /** Set the accessibility label. */
        public Config label(String label) {
            this.label = label;
            return this;
        }
    }

    private final Config config;

    private Icon(Config config) {
        this.config = config;
    }

    /** A static canonical icon. */
    public static Icon of(IconName name) {
        return new Icon(new Config().name(name));
    }

    /** A static canonical icon by canonical name (for names outside the enum). */
    public static Icon of(String canonicalName) {
        return new Icon(new Config().name(canonicalName));
    }

    /** A reactive icon bound to a canonical-name signal. */
    public static Icon of(Signal<String> canonicalName) {
        return new Icon(new Config().name(canonicalName));
    }

    /** A static canonical icon with an accessibility label. */
    public static Icon labeled(IconName name, String label) {
        return new Icon(new Config().name(name).label(label));
    }

    /** Configure the icon's values. */
    public static ViewBuilder<Icon, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Icon(config));
    }

    /** Apply modifiers to a name-less icon. */
    public static ViewBuilder<Icon, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Icon(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.ICON);
        if (config.name != null) {
            node.properties.put(Properties.ICON_NAME, config.name.get());
            node.propertyBindings.put(Properties.ICON_NAME, config.name);
        }
        if (config.label != null && !config.label.isBlank()) {
            node.properties.put(Properties.LABEL, config.label);
        }
        return node;
    }
}
