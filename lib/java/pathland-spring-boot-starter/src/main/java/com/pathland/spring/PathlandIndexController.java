package com.pathland.spring;

import com.pathland.server.PathlandHost;
import com.pathland.server.PathlandHost.MountMatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Server-side rendering entry point. Dispatches each request to the app whose mount path
 * is the longest prefix of the request (see {@link PathlandHost}) and renders that app's
 * session. The per-window id ({@code wid}) lives only on the WebSocket URL (kept in
 * {@code sessionStorage}, never in the page URL), so the SSR request never carries one:
 * it renders defaults, and the client re-syncs that window's persisted state over the
 * WebSocket after a same-tab reload. Without state scope, two windows never share a UI
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

    /** An optional filesystem directory served under {@code /_pathland/**} (e.g. a
     *  temp-dir extraction of the demo's media assets — see the demo hosts). */
    @Value("${pathland.asset-dir:}")
    private String assetDir;

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
     *
     * <p>Byte-range requests ({@code Range}) are honored with {@code 206 Partial
     * Content} + {@code Content-Range}: an {@code <audio>} seeking beyond its buffer
     * issues a range request, and answering it with a full {@code 200} makes browsers
     * restart the media at byte 0. The full response advertises {@code Accept-Ranges}.
     */
    @GetMapping(value = {"/_pathland/**", "/{app}/_pathland/**"}, headers = "!Upgrade")
    @ResponseBody
    public ResponseEntity<?> framework(
            HttpServletRequest request) throws IOException {
        String uri = request.getRequestURI(); // e.g. "/_pathland/dom-renderer.js" or "/app2/_pathland/dom-renderer.js"
        int idx = uri.indexOf("/_pathland/");
        if (idx < 0) {
            return ResponseEntity.notFound().build();
        }
        String file = uri.substring(idx + "/_pathland/".length());
        Resource resource = classpathFrameworkResource(file);
        if (resource == null) {
            resource = diskAsset(file);
        }
        if (resource == null || !resource.exists()) {
            // Renderer-owned icon glyphs have no static file — serve them from the
            // shared Rust renderer (`pathland_html_icon_svg` over the C ABI, the same
            // `icons` source SSR inlines from), so every host serves /_pathland/icons/
            // from ONE library. A host can still override a glyph with a static file.
            ResponseEntity<?> icon = iconGlyph(file);
            if (icon != null) {
                return icon;
            }
            return ResponseEntity.notFound().build();
        }
        // Spring's built-in extension → MIME detection (js, svg, mp4, mp3, …).
        MediaType type = MediaTypeFactory.getMediaType(file)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        if (rangeHeader != null && !rangeHeader.isEmpty()) {
            return rangeResponse(resource, type, rangeHeader);
        }
        try (InputStream in = resource.getInputStream()) {
            return ResponseEntity.ok().contentType(type)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable())
                    .body(in.readAllBytes());
        }
    }

    /** Serve `/_pathland/icons/<name>.svg` from the shared Rust renderer; {@code null}
     *  when the path is not an icon glyph or the renderer is unavailable. */
    private ResponseEntity<?> iconGlyph(String file) {
        if (!file.startsWith("icons/") || !file.endsWith(".svg")) {
            return null;
        }
        String name = file.substring("icons/".length(), file.length() - ".svg".length());
        if (name.isEmpty() || name.indexOf('/') >= 0) {
            return null;
        }
        var renderer = com.pathland.render.html.HtmlRenderer.tryInstance();
        if (renderer == null) {
            return null;
        }
        String svg = renderer.iconSvg(name);
        if (svg == null) {
            return null;
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("image", "svg+xml"))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .body(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** The DOM client bundle (+ any app-local assets) from {@code static/_pathland/**}. */
    private Resource classpathFrameworkResource(String file) {
        ClassPathResource resource = new ClassPathResource("static/_pathland/" + file);
        return resource.exists() ? resource : null;
    }

    /** A media/icon asset from the configured {@code pathland.asset-dir} (fast disk
     *  serving of the demo-views extraction; traversal-guarded). */
    private Resource diskAsset(String file) {
        if (assetDir == null || assetDir.isBlank()) {
            return null;
        }
        Path base = Path.of(assetDir).toAbsolutePath().normalize();
        Path resolved = base.resolve(file).normalize();
        if (!resolved.startsWith(base) || !Files.isRegularFile(resolved)) {
            return null;
        }
        return new FileSystemResource(resolved);
    }

    /** Answer a single {@code Range: bytes=…} request with the partial content
     *  ({@code 206}) or {@code 416} for an unsatisfiable range. */
    private ResponseEntity<?> rangeResponse(Resource resource, MediaType type, String rangeHeader)
            throws IOException {
        long length = resource.contentLength();
        ResponseEntity<?> unsatisfiable = ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + length)
                .build();
        String spec = rangeHeader.startsWith("bytes=") ? rangeHeader.substring("bytes=".length()).trim() : null;
        long start;
        long end;
        try {
            if (spec == null || spec.isEmpty() || spec.contains(",")) {
                return unsatisfiable; // multi-range not needed for media; be strict
            }
            if (spec.startsWith("-")) {
                long suffix = Long.parseLong(spec.substring(1).trim());
                if (suffix <= 0) {
                    return unsatisfiable;
                }
                start = Math.max(0, length - suffix);
                end = length - 1;
            } else {
                String[] parts = spec.split("-", 2);
                start = Long.parseLong(parts[0].trim());
                end = parts.length > 1 && !parts[1].isBlank() ? Long.parseLong(parts[1].trim()) : length - 1;
            }
        } catch (NumberFormatException e) {
            return unsatisfiable;
        }
        if (start < 0 || start >= length || end < start) {
            return unsatisfiable;
        }
        end = Math.min(end, length - 1);
        byte[] body;
        try (InputStream in = resource.getInputStream()) {
            in.skipNBytes(start);
            body = in.readNBytes((int) (end - start + 1));
        }
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(type)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable())
                .contentLength(body.length)
                .body(body);
    }
}