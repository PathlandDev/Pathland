package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * Window/surface presentation metadata ({@code windowMetadata(...)}): title,
 * description, and locale for the host chrome that owns the stream
 * ({@code META::SET_WINDOW_METADATA}, spec/OPCODE.md §Window metadata).
 *
 * <p>This is renderer-mapped, not browser-specific: the browser renderer maps
 * title/description/locale to the document title, meta description and
 * {@code <html lang>}; a native renderer maps the title to its window. It is
 * <b>root-scoped</b> — declared on the root view, applying to the whole surface.
 *
 * <p>Each field holds a single {@link Signal}; a static value is sugar for a
 * constant signal. A reactive field re-emits only that field on change, so a
 * route title is a {@code computed} over the router:
 *
 * <pre>{@code
 * root.modifiers(windowMetadata(w -> w
 *     .title(computed(() -> titleFor(router.path())))
 *     .description(computed(() -> descriptionFor(router.path())))
 *     .locale("en")));
 * }</pre>
 */
public final class WindowMetadata implements ViewModifier {

    /** {@link WindowMetadata} values. */
    public static final class Config {

        private Signal<String> title;
        private Signal<String> description;
        private Signal<String> locale;

        /** Set a static window title. */
        public Config title(String value) {
            this.title = Signals.constant(value);
            return this;
        }

        /** Bind the window title to a signal. */
        public Config title(Signal<String> signal) {
            this.title = signal;
            return this;
        }

        /** Set a static description. */
        public Config description(String value) {
            this.description = Signals.constant(value);
            return this;
        }

        /** Bind the description to a signal. */
        public Config description(Signal<String> signal) {
            this.description = signal;
            return this;
        }

        /** Set a static content locale (BCP 47). */
        public Config locale(String value) {
            this.locale = Signals.constant(value);
            return this;
        }

        /** Bind the content locale to a signal. */
        public Config locale(Signal<String> signal) {
            this.locale = signal;
            return this;
        }
    }

    private final Signal<String> title;
    private final Signal<String> description;
    private final Signal<String> locale;

    private WindowMetadata(Config config) {
        this.title = config.title;
        this.description = config.description;
        this.locale = config.locale;
    }

    /** Configure the window/surface metadata. */
    static WindowMetadata with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new WindowMetadata(config);
    }

    /** The title signal, or null when unset. */
    public Signal<String> title() {
        return title;
    }

    /** The description signal, or null when unset. */
    public Signal<String> description() {
        return description;
    }

    /** The locale signal, or null when unset. */
    public Signal<String> locale() {
        return locale;
    }

    @Override
    public View body(View content) {
        return new WindowMetadataView(content, this);
    }

    /**
     * Records this metadata in the render environment (it applies to the whole
     * surface) and renders the wrapped content unchanged. The emitter reads it
     * after the mount render and binds each field to its signal.
     */
    private static final class WindowMetadataView implements View {

        private final View content;
        private final WindowMetadata metadata;

        WindowMetadataView(View content, WindowMetadata metadata) {
            this.content = content;
            this.metadata = metadata;
        }

        @Override
        public PathlandNode render(Environment env) {
            env.setWindowMetadata(metadata);
            return content.render(env);
        }
    }
}
