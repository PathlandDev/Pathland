package dev.pathland.render.html;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The renderer's built-in base design, copied from the generated
 * {@code design/build/base.css} bundle (the same bytes the Rust renderer
 * embeds).
 */
final class BaseCss {

    private static final String CSS = read("/dev/pathland/render/html/base.css");
    private static final String NAME = read("/dev/pathland/render/html/base.css.name").trim();

    private BaseCss() {}

    static String builtinCss() {
        return CSS;
    }

    static String builtinCssName() {
        return NAME;
    }

    static String styleElement() {
        return "<style>" + CSS + "</style>";
    }

    private static String read(String resource) {
        try (InputStream stream = BaseCss.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return "";
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
