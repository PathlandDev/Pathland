package dev.pathland.quarkus;

import dev.pathland.server.PathlandHost;
import dev.pathland.server.PathlandHost.MountMatch;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.io.IOException;
import java.io.InputStream;

/**
 * Server-side rendering entry point. Dispatches each request to the app whose mount path
 * is the longest prefix of the request (see {@link PathlandHost}) and renders that app's
 * session. The client's per-window id ({@code ?wid=…}, preserved in the URL by the DOM
 * client) scopes the render to that window's <b>persisted state</b>, so a reload's HTML is
 * already the latest UI model state and needs no resync; without it the page renders
 * defaults and the client re-syncs once. Two windows of the same browser never share a UI
 * model or state.
 *
 * <p>The initial route is part of the platform environment (spec/OPCODE.md §Environment
 * fields): the request path is synthesized into the environment's {@code ROUTE} field, so
 * a deep link like {@code /users/42} renders its destination on the first paint — with the
 * app's mount prefix stripped, so the app always sees its own route space.
 */
@Path("/")
public class IndexResource {

    @Inject
    PathlandHost host;

    /** Content-hashed assets never change under a given name. */
    private static final String IMMUTABLE = "public, max-age=31536000, immutable";

    /** The root page (the route is {@code /}). */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response index(@QueryParam("wid") String wid) {
        return render("/", wid);
    }

    /** A valid crawler policy — never the SPA HTML fallback. */
    @GET
    @Path("/robots.txt")
    @Produces(MediaType.TEXT_PLAIN)
    public Response robots(@Context UriInfo uri) {
        String base = uri.getBaseUri().toString();
        String body = "User-agent: *\nAllow: /\nSitemap: " + base + "sitemap.xml\n";
        return Response.ok(body).build();
    }

    /** A one-entry sitemap for the app root (a host may mount more routes). */
    @GET
    @Path("/sitemap.xml")
    @Produces(MediaType.APPLICATION_XML)
    public Response sitemap(@Context UriInfo uri) {
        String loc = uri.getBaseUri().toString();
        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"
                + "<url><loc>" + loc + "</loc></url></urlset>\n";
        return Response.ok(body).build();
    }

    /**
     * Any other path (deep links) — a multi-segment catch-all. The negative lookahead
     * excludes a path that <em>starts</em> with the reserved {@code _pathland} prefix (the
     * global WebSocket + DOM client bundle, served by Quarkus static resources from
     * {@code META-INF/resources} before JAX-RS) and the Quarkus **non-application root**
     * ({@code /q/**} — health, metrics, …), which the framework serves. Per-app framework
     * paths ({@code /<app>/_pathland/...}, e.g. a mounted app's bundle) are intercepted
     * here and served from the shared {@code /_pathland/**} classpath mount.
     */
    @GET
    @Path("{path:(?!_pathland)(?!q/).*}")
    @Produces(MediaType.TEXT_HTML)
    public Response deep(@PathParam("path") String path, @QueryParam("wid") String wid) throws IOException {
        int framework = path.indexOf("/_pathland/");
        if (framework >= 0) {
            return frameworkFile(path.substring(framework + "/_pathland/".length()));
        }
        return render("/" + path, wid);
    }

    private Response render(String requestUri, String wid) {
        MountMatch match = host.match(requestUri);
        if (match == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(match.registry().renderHtml(match.route(), wid)).build();
    }

    /** The renderer-owned icon glyphs at the global base — `/_pathland/icons/<name>.svg`.
     *  These have no static file: every host serves them from the shared Rust renderer
     *  (`pathland_html_icon_svg` over the C ABI, the same `icons` source SSR inlines). */
    @GET
    @Path("/_pathland/icons/{name}.svg")
    @Produces("image/svg+xml")
    public Response icon(@PathParam("name") String name) {
        return iconResponse(name);
    }

    /** The renderer-owned content-hashed base stylesheet at the global base. */
    @GET
    @Path("/_pathland/{name}.css")
    @Produces("text/css")
    public Response baseCss(@PathParam("name") String name) {
        Response css = baseCssResponse(name + ".css");
        return css != null ? css : Response.status(Response.Status.NOT_FOUND).build();
    }

    /** Serve a mounted app's framework file (its {@code data-pathland-base} bundle) from the shared classpath mount. */
    private Response frameworkFile(String file) throws IOException {
        // Quarkus web static lives in META-INF/resources (classpath resource root), so the
        // shared `/_pathland/**` mount resolves as /META-INF/resources/_pathland/<file>.
        try (InputStream in = getClass().getResourceAsStream("/META-INF/resources/_pathland/" + file)) {
            if (in != null) {
                return Response.ok(in.readAllBytes()).type(mediaTypeOf(file)).build();
            }
        }
        // No static file (a host override would be served above) — the base design
        // is renderer-owned and content-hashed.
        Response css = baseCssResponse(file);
        if (css != null) {
            return css;
        }
        // Renderer-owned icon glyphs come from the shared Rust renderer; a
        // single-segment name is required (the DOM client requests exactly
        // {base}/icons/<encodeURIComponent>).
        if (file.startsWith("icons/") && file.endsWith(".svg")
                && file.substring("icons/".length(), file.length() - ".svg".length()).indexOf('/') < 0) {
            return iconResponse(file.substring("icons/".length(), file.length() - ".svg".length()));
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    /** The renderer-owned content-hashed base stylesheet, or {@code null}. */
    private Response baseCssResponse(String file) {
        var renderer = dev.pathland.render.html.HtmlRenderer.tryInstance();
        if (renderer == null) {
            return null;
        }
        String name = renderer.baseCssName();
        if (name.isEmpty() || !name.equals(file)) {
            return null;
        }
        String css = renderer.baseCss();
        if (css.isEmpty()) {
            return null;
        }
        return Response.ok(css.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .type("text/css")
                .header("Cache-Control", IMMUTABLE)
                .build();
    }

    /** The renderer-owned `/_pathland/icons/<name>.svg` from the shared Rust library. */
    private Response iconResponse(String name) {
        var renderer = dev.pathland.render.html.HtmlRenderer.tryInstance();
        String svg = renderer == null ? null : renderer.iconSvg(name);
        if (svg == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .type(new MediaType("image", "svg+xml"))
                .header("Cache-Control", "public, max-age=2592000, immutable")
                .build();
    }

    private static MediaType mediaTypeOf(String file) {
        if (file.endsWith(".js")) {
            return new MediaType("application", "javascript");
        }
        if (file.endsWith(".svg")) {
            return new MediaType("image", "svg+xml");
        }
        if (file.endsWith(".mp3")) {
            return new MediaType("audio", "mpeg");
        }
        if (file.endsWith(".mp4")) {
            return new MediaType("video", "mp4");
        }
        return MediaType.APPLICATION_OCTET_STREAM_TYPE;
    }
}