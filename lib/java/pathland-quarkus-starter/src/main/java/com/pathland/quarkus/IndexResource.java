package com.pathland.quarkus;

import com.pathland.server.PathlandRegistry;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

/**
 * Server-side rendering entry point. Sets a per-session cookie and renders that session's
 * persisted state. The browser sends the same cookie on the WebSocket handshake (1:1).
 *
 * <p>The initial route is part of the platform environment (spec/OPCODE.md §Environment
 * fields): the request path is synthesized into the environment's {@code ROUTE} field, so
 * a deep link like {@code /users/42} renders its destination on the first paint.
 */
@Path("/")
public class IndexResource {

    @Inject
    PathlandRegistry registry;

    /** The root page (the route is {@code /}). */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response index(@CookieParam("session") String sessionId) {
        return render("/", sessionId);
    }

    /**
     * Any other path (deep links) — a multi-segment catch-all. The negative lookahead
     * excludes the reserved {@code /_pathland/**} framework prefix (the WebSocket, the
     * DOM client bundle, and the asset mount — served by Quarkus static resources from
     * {@code META-INF/resources} before JAX-RS), so system endpoints are never SSR'd.
     */
    @GET
    @Path("{path:(?!_pathland).*}")
    @Produces(MediaType.TEXT_HTML)
    public Response deep(@PathParam("path") String path, @CookieParam("session") String sessionId) {
        return render("/" + path, sessionId);
    }

    private Response render(String route, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        String html = registry.renderHtml(sessionId, route);
        NewCookie cookie = new NewCookie.Builder("session").value(sessionId).path("/").build();
        return Response.ok(html).cookie(cookie).build();
    }
}