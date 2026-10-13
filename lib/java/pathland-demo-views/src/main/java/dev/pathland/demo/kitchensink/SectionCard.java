package dev.pathland.demo.kitchensink;

import dev.pathland.view.AccessibilityRole;
import dev.pathland.view.Background;
import dev.pathland.view.Border;
import dev.pathland.view.ChildrenView;
import dev.pathland.view.Color;
import dev.pathland.view.Commands;
import dev.pathland.view.Configurable;
import dev.pathland.view.CornerRadius;
import dev.pathland.view.Divider;
import dev.pathland.view.FontSize;
import dev.pathland.view.FontWeight;
import dev.pathland.view.ForegroundStyle;
import dev.pathland.view.Frame;
import dev.pathland.view.Padding;
import dev.pathland.view.Roles;
import dev.pathland.view.Text;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import dev.pathland.view.ViewBuilder;
import dev.pathland.view.ViewModifier;
import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;
import static dev.pathland.view.Modifiers.*;

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
                        padding(16),
                        background(Color.WHITE),
                        border(b -> b.color(BORDER).width(1).radius(10)),
                        cornerRadius(10),
                        frame(f -> f.width(Commands.Size.FILL)))
                .children(
                        // A section title is a heading → `<h2>`.
                        Text.make(config.title).modifiers(
                                fontSize(14), FontWeight.SEMIBOLD,
                                foregroundStyle(TITLE_COLOR),
                                accessibilityRole(Roles.HEADER)),
Divider.make().modifiers(padding(p -> p.edges(0, 0, 8, 0))),
                        content);
    }
}