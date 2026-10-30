package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;

import java.util.function.Consumer;

/**
 * A **proxy view** ({@code PROXY_VIEW} 0x1E, spec/PROXY.md): a transparent
 * single-child container whose child subtree is served by **another** Pathland
 * server. It lets one app compose views from several servers into a single
 * unified document.
 *
 * <p>Unlike the other containers it is <b>not</b> {@code children(...)}-bearing:
 * the app does not author the subtree. The proxying server fetches it from the
 * origin ({@code META::RESYNC}), <b>remaps its node ids</b> into a disjoint high
 * range, and splices it in. The proxy node itself emits only its
 * {@code CREATE_NODE} (+ any size modifiers); the origin root is attached under
 * it at runtime.
 *
 * <pre>{@code
 * ProxyView.make(p -> p.endpoint("https://b.example").route("/music"));
 * }</pre>
 */
public final class ProxyView implements View, Configurable<ProxyView.Config> {

    /** {@link ProxyView} values. */
    public static final class Config implements View.Config {

        private String endpoint;
        private String route = "/";
        private String credential;

        /** The origin server's base URL (required). */
        public Config endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /** The route the origin should render (default {@code "/"}). */
        public Config route(String route) {
            this.route = route;
            return this;
        }

        /** The service-credential name to present on the origin handshake. */
        public Config credential(String credential) {
            this.credential = credential;
            return this;
        }
    }

    private final Config config;

    private ProxyView(Config config) {
        this.config = config;
    }

    /** Configure the proxy's origin endpoint/route/credential. */
    public static ViewBuilder<ProxyView, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new ProxyView(config));
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode renderNode(Environment env) {
        PathlandNode node = new PathlandNode(Components.PROXY_VIEW);
        node.proxyConfig = new ProxyConfig(config.endpoint, config.route, config.credential);
        return node;
    }
}
