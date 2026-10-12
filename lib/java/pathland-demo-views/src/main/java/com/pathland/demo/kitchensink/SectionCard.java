package com.pathland.demo.kitchensink;

import com.pathland.view.AccessibilityRole;
import com.pathland.view.Background;
import com.pathland.view.Border;
import com.pathland.view.ChildrenView;
import com.pathland.view.Color;
import com.pathland.view.Commands;
import com.pathland.view.Configurable;
import com.pathland.view.CornerRadius;
import com.pathland.view.Divider;
import com.pathland.view.FontSize;
import com.pathland.view.FontWeight;
import com.pathland.view.ForegroundStyle;
import com.pathland.view.Frame;
import com.pathland.view.Padding;
import com.pathland.view.Roles;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.ViewBuilder;
import com.pathland.view.ViewModifier;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.List;
import java.util.function.Consumer;

/**
 * A reusable kitchen-sink section card: a titled, bordered, rounded container that
 * hosts one section's controls. Demonstrates composition — a custom {@code View}
 * wrapping arbitrary content with style modifiers.
 *
 * <p>Configured with {@code SectionCard.make(s -> s.title(...)).children(content)}
 * — a private constructor keeps creation on the configurator (spec DSL.md §2).
 */
public final class SectionCard implements View, Configurable<SectionCard.Config>, ChildrenView {

    /** {@link SectionCard} values. */
    public static final class Config implements View.Config {

        private Signal<String> title;

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
    }

    private static final Color TITLE_COLOR = Color.rgb(0x55, 0x64, 0x6D);
    private static final Color BORDER = Color.rgb(0xE2, 0xE8, 0xF0);

    private final Config config;
    private List<View> children = List.of();

    private SectionCard(Config config) {
        this.config = config;
    }

    /** A section card with a static title ({@code SectionCard.make("…")}). */
    public static ViewBuilder<SectionCard, Config> make(String title) {
        return make(c -> c.title(title));
    }

    /** A section card with a reactive title. */
    public static ViewBuilder<SectionCard, Config> make(Signal<String> title) {
        return make(c -> c.title(title));
    }

    /** Configure the section card's values. */
    public static ViewBuilder<SectionCard, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new SectionCard(config));
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public void setChildren(List<View> children) {
        this.children = List.copyOf(children);
    }

    @Override
    public View body() {
        View content = children.isEmpty() ? null : children.get(0);
        return VStack.make()
                .modifiers(
                        Padding.with(16),
                        Background.with(Color.WHITE),
                        Border.with(b -> b.color(BORDER).width(1).radius(10)),
                        CornerRadius.with(10),
                        Frame.with(f -> f.width(Commands.Size.FILL)))
                .children(
                        // A section title is a heading → `<h2>`.
                        Text.make(config.title).modifiers(
                                FontSize.with(14), FontWeight.SEMIBOLD,
                                ForegroundStyle.with(TITLE_COLOR),
                                AccessibilityRole.with(Roles.HEADER)),
Divider.make().modifiers(Padding.with(p -> p.edges(0, 0, 8, 0))),
                        content);
    }
}