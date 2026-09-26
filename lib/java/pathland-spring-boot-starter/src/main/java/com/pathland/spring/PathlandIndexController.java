package com.pathland.spring;

import com.pathland.server.PathlandRegistry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Server-side rendering entry point. Sets a per-session cookie and renders that session's
 * persisted state. The browser sends the same cookie on the WebSocket handshake (1:1).
 *
 * <p>The initial route is part of the platform environment (spec/OPCODE.md §Environment
 * fields): the request path is synthesized into the environment's {@code ROUTE} field, so
 * a deep link like {@code /users/42} renders its destination on the first paint. The
 * WebSocket later enriches the environment (viewport, …).
 */
@Controller
public class PathlandIndexController {

    private final PathlandRegistry registry;

    public PathlandIndexController(PathlandRegistry registry) {
        this.registry = registry;
    }

    /**
     * The SPA catch-all: every path renders the session shell seeded at the request path.
     * Requests carrying an {@code Upgrade} header (the {@code /_pathland/ws} WebSocket
     * handshake) are excluded so they reach the registered WebSocket handler, not this
     * HTML renderer.
     */
    @GetMapping(value = "/{*path}", headers = "!Upgrade")
    @ResponseBody
    public ResponseEntity<String> index(
            @CookieValue(value = "session", required = false) String sessionId,
            HttpServletRequest request) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        String route = request.getRequestURI(); // e.g. "/users/42" or "/"
        String html = registry.renderHtml(sessionId, route);
        ResponseCookie cookie = ResponseCookie.from("session", sessionId).path("/").build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    /**
     * The reserved framework prefix — serves the DOM client bundle and the asset
     * mount from {@code classpath:/_pathland/**} so the {@code /{*path}} catch-all
     * never shadows them (a more-specific mapping wins). WebSocket upgrades
     * ({@code Upgrade} header — {@code /_pathland/ws}) are excluded so the registered
     * WebSocket handler takes the handshake, not this static controller.
     */
    @GetMapping(value = "/_pathland/**", headers = "!Upgrade")
    @ResponseBody
    public ResponseEntity<byte[]> framework(
            HttpServletRequest request) throws IOException {
        String uri = request.getRequestURI(); // e.g. "/_pathland/dom-renderer.js"
        ClassPathResource resource = new ClassPathResource("static" + uri);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        // Spring's built-in extension → MIME detection (js, svg, mp4, mp3, …).
        MediaType type = MediaTypeFactory.getMediaType(uri)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        try (InputStream in = resource.getInputStream()) {
            return ResponseEntity.ok().contentType(type).body(in.readAllBytes());
        }
    }
}