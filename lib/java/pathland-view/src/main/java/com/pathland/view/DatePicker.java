package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

/**
 * A date & time selection control ({@code DatePicker}). The value is carried by
 * the {@code PARAMETER::SET_DATE} command ({@code B}=days since epoch, {@code C}=millis of
 * day); the bound signal holds days since epoch. Renders with the
 * {@code DATE_PICKER_MODE} token.
 */
public final class DatePicker implements View, Configurable<DatePicker.Config> {

    /** {@link DatePicker} values. */
    public static final class Config implements View.Config {

        private DatePickerMode mode;
        private WritableSignal<Integer> days;

        /** Set the picker mode. */
        public Config mode(DatePickerMode mode) {
            this.mode = mode;
            return this;
        }

        /** Bind the selected day (days since epoch, two-way). */
        public Config selection(WritableSignal<Integer> days) {
            this.days = days;
            return this;
        }
    }

    private final Config config;

    private DatePicker(Config config) {
        this.config = config;
    }

    /** A date & time picker bound to days-since-epoch. */
    public static DatePicker of(DatePickerMode mode, WritableSignal<Integer> days) {
        return new DatePicker(new Config().mode(mode).selection(days));
    }

    /** Configure the date picker's values. */
    public static ViewBuilder<DatePicker, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new DatePicker(config));
    }

    /** Apply modifiers to a bare date picker. */
    public static ViewBuilder<DatePicker, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new DatePicker(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.DATE_PICKER);
        if (config.mode != null) {
            node.properties.put(Properties.DATE_PICKER_MODE, (float) config.mode.wire());
        }
        if (config.days != null) {
            node.days = config.days.get();
            node.dateBinding = config.days;
            node.dateInput = (d, m) -> config.days.set(d);
        }
        return node;
    }
}
