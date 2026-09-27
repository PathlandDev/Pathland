package com.pathland.spring;

import com.pathland.server.PathlandHost;
import com.pathland.server.PathlandHost.MountMatch;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;

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
 * app's mount prefix stripped, so the app always sees its own route space. The WebSocket
 * later enriches the environment (viewport, …).
 */
@Controller
public class PathlandIndexController {

    private final PathlandHost host;

    public PathlandIndexController(PathlandHost host) {
        this.host = host;
    }

    /**
     * The SPA catch-all: every path renders the session shell seeded at the request path,
     * dispatched to the mounted app that owns the path. Requests carrying an
     * {@code Upgrade} header (WebSocket handshakes) are excluded so they reach the
     * registered WebSocket handlers, not this HTML renderer.
     */
    @GetMapping(value = "/{*path}", headers = "!Upgrade")
    @ResponseBody
    public ResponseEntity<String> index(HttpServletRequest request) {
        MountMatch match = host.match(request.getRequestURI());
        if (match == null) {
            return ResponseEntity.notFound().build();
        }
        String html = match.registry().renderHtml(match.route(), request.getParameter("wid"));
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    /**
     * The reserved framework prefix — serves the DOM client bundle and the asset
     * mount from {@code classpath:/_pathland/**}, both at the global base
     * ({@code /_pathland/**}) and at every mounted app's base ({@code /<path>/_pathland/**}),
     * so the per-app {@code data-pathland-base} the SSR page emits resolves the same
     * bundle. WebSocket upgrades ({@code Upgrade} header — {@code <mount>/_pathland/ws}) are
     * excluded so the registered WebSocket handlers take the handshake.
     */
    @GetMapping(value = {"/_pathland/**", "/{app}/_pathland/**"}, headers = "!Upgrade")
    @ResponseBody
    public ResponseEntity<byte[]> framework(
            HttpServletRequest request) throws IOException {
        String uri = request.getRequestURI(); // e.g. "/_pathland/dom-renderer.js" or "/app2/_pathland/dom-renderer.js"
        int idx = uri.indexOf("/_pathland/");
        if (idx < 0) {
            return ResponseEntity.notFound().build();
        }
        String file = uri.substring(idx + "/_pathland/".length());
        ClassPathResource resource = new ClassPathResource("static/_pathland/" + file);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        // Spring's built-in extension → MIME detection (js, svg, mp4, mp3, …).
        MediaType type = MediaTypeFactory.getMediaType(file)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        try (InputStream in = resource.getInputStream()) {
            return ResponseEntity.ok().contentType(type).body(in.readAllBytes());
        }
    }
}