package com.pathland.view.router;

import com.pathland.view.Button;
import com.pathland.view.Configurable;
import com.pathland.view.Environment;
import com.pathland.view.Text;
import com.pathland.view.View;
import com.pathland.view.ViewBuilder;
import com.pathland.view.ViewModifier;
import com.pathland.view.emit.PathlandNode;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A tappable link that changes the route (spec DSL.md §4.5). With an explicit
 * {@link Router} it {@link Router#push(String) pushes} onto that router's
 * back-stack; the router-agnostic call ({@code NavigationLink.with(l -> l.to("/users"))})
 * declares a {@link NavOp#PUSH} intent that the emitter resolves to the **nearest
 * enclosing** {@link Router} — no router is threaded by hand. Renders through the
 * active {@code ButtonStyle} as a native {@code BUTTON}; its tap action is routed
 * via the emitter's tap/navigate-action registry exactly like a button.
 */
public final class NavigationLink implements View, Configurable<NavigationLink.Config> {

    /** {@link NavigationLink} values. */
    public static final class Config implements View.Config {

        private String label;
        private View labelView;
        private Router router;
        private String to;

        /** A plain text label. */
        public Config label(String label) {
            this.label = label;
            this.labelView = null;
            return this;
        }

        /** An arbitrary child view as the label. */
        public Config label(View label) {
            this.labelView = label;
            this.label = null;
            return this;
        }

        /** The explicit router to push onto (or {@code null} for the router-agnostic form). */
        public Config router(Router router) {
            this.router = router;
            return this;
        }

        /** The destination path. */
        public Config to(String to) {
            this.to = to;
            return this;
        }
    }

    private final Config config;

    private NavigationLink(Config config) {
        this.config = config;
    }

    /** Configure the link ({@code NavigationLink.with(l -> l.label(...).to(...))}). */
    public static ViewBuilder<NavigationLink, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new NavigationLink(config));
    }

    /** Apply modifiers to a bare link. */
    public static ViewBuilder<NavigationLink, Config> modifiers(ViewModifier... modifiers) {
        Config config = new Config();
        config.to("");
        return new ViewBuilder<>(new NavigationLink(config)).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        Objects.requireNonNull(config.to, "to");
        View label = config.labelView != null
                ? config.labelView
                : Text.with(t -> t.text(config.label == null ? "" : config.label));
        if (config.router != null) {
            return Button.children(label)
                    .with(b -> b.action(() -> config.router.push(config.to)))
                    .render(env);
        }
        // The tap's real effect is the declared navigation intent (`.push`), which the
        // host routes via navigateActions before tapActions; the button action is a
        // no-op placeholder (Button requires one).
        return Button.children(label)
                .with(b -> b.action(() -> {}))
                .modifiers(NavigationIntent.push(config.to))
                .render(env);
    }
}