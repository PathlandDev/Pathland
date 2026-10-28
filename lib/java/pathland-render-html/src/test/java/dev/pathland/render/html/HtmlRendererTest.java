package dev.pathland.render.html;

import dev.pathland.view.AccessibilityLabel;
import dev.pathland.view.AccessibilityRole;
import dev.pathland.view.MediaController;
import dev.pathland.view.Button;
import dev.pathland.view.DatePicker;
import dev.pathland.view.DatePickerMode;
import dev.pathland.view.Divider;
import dev.pathland.view.Environment;
import dev.pathland.view.Image;
import dev.pathland.view.Picker;
import dev.pathland.view.PickerStyle;
import dev.pathland.view.ProgressView;
import dev.pathland.view.Roles;
import dev.pathland.view.ScaledToFill;
import dev.pathland.view.Slider;
import dev.pathland.view.Text;
import dev.pathland.view.TextEditor;
import dev.pathland.view.Toggle;
import dev.pathland.view.ToggleStyle;
import dev.pathland.view.Video;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import dev.pathland.view.emit.Emitter;
import dev.pathland.view.emit.ProtocolFrame;
import dev.pathland.view.emit.FrameOpcodeSink;
import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;
import dev.pathland.view.signal.WritableSignal;
import dev.pathland.view.Font;

import static dev.pathland.view.Modifiers.*;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DSL -> emitter -> frame -> Rust HtmlRenderer shim (the SSR round-trip). */
class HtmlRendererTest {

    private static HtmlRenderer renderer() {
        try {
            return HtmlRenderer.instance();
        } catch (Throwable t) {
            Assumptions.assumeTrue(false,
                    "libpathland_render_html unavailable on java.library.path; shim test skipped: " + t);
            return null;
        }
    }

    private static ProtocolFrame frameOf(View root) {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        new Emitter(sink).mount(root, Environment.DEFAULT);
        return sink.frame();
    }

    @Test
    void rendersVStackOfTextAndButton() {
        View root = VStack.children(
                Text.make(t -> t.text("Hello Pathland")),
                Button.make(b -> b.title("Increment").action(() -> { })));
        ProtocolFrame frame = frameOf(root);
        HtmlRenderer renderer = renderer();
        String html = renderer.render(frame, 1);

        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("flex-direction:column"), "stack rendered inline flex");
        // Typography is the platform system font stack — no external webfont.
        assertFalse(html.contains("rsms.me"), "no font CDN link");
        assertFalse(html.contains("<link rel=\"stylesheet\""), "no external stylesheets");
        assertTrue(html.contains("--pl-font-body-family: system-ui, -apple-system"), "system font stack");
        assertTrue(html.contains("<span data-pathland-id=\"2\">Hello Pathland</span>"));
        assertTrue(html.contains("<button data-pathland-id=\"3\""), "button keeps its node id");
        assertTrue(html.contains("class=\"pathland-button\""), "button uses the design-system class");
        // Wrapper model: the label is the button's child (a node of its own).
        assertTrue(html.contains("<span data-pathland-id=\"4\">Increment</span>"),
                "the styled label renders as the button's child");
        assertTrue(html.contains("</button>"));
    }

    @Test
    void renderLinkedEmitsTheContentHashedStylesheet() {
        ProtocolFrame frame = frameOf(Text.make(t -> t.text("Hi")));
        HtmlRenderer renderer = renderer();
        String name = renderer.baseCssName();
        assertTrue(name.matches("base-[0-9a-f]{12}\\.css"), "hashed asset name: " + name);
        String css = renderer.baseCss();
        assertFalse(css.isEmpty(), "base CSS bytes are exposed");
        assertTrue(css.contains("--pl-color-primary"), "base design bytes: " + css);

        String html = renderer.renderLinked(frame, 1, "/_pathland");
        assertTrue(html.contains("<link rel=\"stylesheet\" href=\"/_pathland/" + name + "\">"),
                "linked stylesheet: " + html);
        assertFalse(html.contains("<style>"), "base CSS is not inlined: " + html);
        // The default API stays inline.
        assertTrue(renderer.render(frame, 1).contains("<style>"), "default render remains inline");
    }

    @Test
    void rendersFullSnapshotFragment() {
        View root = VStack.children(Text.make(t -> t.text("Static")),
                Text.make(t -> t.text(Signals.computed(() -> "Count: 0"))));
        HtmlRenderer renderer = renderer();
        String html = renderer.renderFragment(frameOf(root), 1);
        assertTrue(html.contains("Count: 0"));
        assertFalse(html.contains("<!DOCTYPE html>"));
        // renderFullSnapshot is the same stateless fragment render.
        assertTrue(renderer.renderFullSnapshot(frameOf(root), 1).contains("Count: 0"));
    }

    @Test
    void rendersBorderedStyledButton() {
        View root = VStack.children(Button.make(b -> b.title("Go").action(() -> { })))
                .modifiers(dev.pathland.view.BorderedButtonStyle.INSTANCE);
        String html = renderer().render(frameOf(root), 1);
        assertTrue(html.contains("data-pathland-id=\"2\""), "styled button keeps its node id");
    }

    @Test
    void rendersControls() {
        WritableSignal<Float> value = Signals.signal(0.5f);
        WritableSignal<Boolean> on = Signals.signal(true);
        WritableSignal<Integer> choice = Signals.signal(0);
        View root = VStack.children(
                Toggle.make(t -> t.style(ToggleStyle.CHECKBOX).isOn(on).label("Go")),
                Slider.make(s -> s.value(value).in(0f, 1f)),
                Picker.make(p -> p.style(PickerStyle.MENU).selection(choice))
                        .children(Text.make(t -> t.text("A")), Text.make(t -> t.text("B"))),
                ProgressView.make(p -> p.value(0.4f)),
Divider.make().modifiers(),
                TextEditor.make(e -> e.text(Signals.signal("hello"))),
                DatePicker.make(d -> d.mode(DatePickerMode.DATE).selection(Signals.signal(20487))));
        String html = renderer().render(frameOf(root), 1);

        assertTrue(html.contains("type=\"checkbox\""), "toggle checkbox");
        assertTrue(html.contains("type=\"range\""), "slider");
        assertTrue(html.contains("value=\"0\" selected>A</option>"), "picker options (first)");
        assertTrue(html.contains("value=\"1\">B</option>"), "picker options (second)");
        assertTrue(html.contains("<progress"), "progress bar");
        assertTrue(html.contains("border-top:"), "divider (border-top div)");
        assertTrue(html.contains("<textarea"), "text editor");
        assertTrue(html.contains("type=\"date\""), "date picker");
    }

    @Test
    void escapesText() {
        String html = renderer().renderFragment(frameOf(VStack.children(Text.make(t -> t.text("<a & b>")))), 1);
        assertTrue(html.contains("&lt;a &amp; b&gt;"));
    }

    @Test
    void rendersSemanticRoleElements() {
        // A generic stack with a NAVIGATION role renders as a <nav> landmark; the
        // element conveys the role, so no redundant ARIA attribute is emitted.
        View nav = VStack.children(Text.make(t -> t.text("Menu")))
                .modifiers(accessibilityRole(a -> a.role(Roles.NAVIGATION)));
        String navHtml = renderer().renderFragment(frameOf(nav), 1);
        assertTrue(navHtml.contains("<nav "), "navigation role -> <nav>");
        assertFalse(navHtml.contains("role=\"navigation\""), "nav conveys the role (no ARIA attr)");

        // A generic Text with a HEADER (heading) role renders as <h2>.
        View heading = Text.make(t -> t.text("Title"))
                .modifiers(accessibilityRole(a -> a.role(Roles.HEADER)));
        String headingHtml = renderer().renderFragment(frameOf(heading), 1);
        assertTrue(headingHtml.contains("<h2 "), "header role -> <h2>");

        // A heading TEXT_STYLE typography makes a Text a heading even without a
        // role (Font.title2() -> <h3>); a non-heading style stays a <span>.
        View title = Text.make(t -> t.text("Sub")).modifiers(Font.title2());
        String titleHtml = renderer().renderFragment(frameOf(title), 1);
        assertTrue(titleHtml.contains("<h3 "), "title2 typography -> <h3>");

        View sub = Text.make(t -> t.text("Sub")).modifiers(Font.subheadline());
        String subHtml = renderer().renderFragment(frameOf(sub), 1);
        assertTrue(subHtml.contains("<span "), "subheadline typography stays a <span>");
    }

    @Test
    void rejectsControlRoles() {
        // Interactive/control roles are NOT roles — they are intrinsic to the
        // control components, so the DSL rejects them.
        assertThrows(IllegalArgumentException.class, () -> accessibilityRole(20), "reserved code rejected");
    }

    @Test
    void rendersMedia() {
        // A `VIDEO` surface over its media controller renders with renderer-native
        // controls (spec/MEDIA.md); an image with an accessibility label renders its
        // `alt` and ContentMode Fill → cover.
        MediaController player = new MediaController("https://example.com/sample.mp4");
        View root = VStack.children(
                player.attach(),
                Video.make(player),
                Image.make(i -> i.source("/_pathland/assets/icons/home.svg"))
                        .modifiers(accessibilityLabel(l -> l.text("Home")))
                        .modifiers(scaledToFill())
        );
        String html = renderer().renderFragment(frameOf(root), 1);
        assertTrue(html.contains("<video"), "video element");
        assertTrue(html.contains("controls"), "video native controls");
        assertTrue(html.contains("data-pathland-controller"), "video binds its controller");
        assertTrue(html.contains("src=\"https://example.com/sample.mp4\""), "the controller's resource");
        assertTrue(html.contains("alt=\"Home\""), "image alt from accessibility label");
        assertTrue(html.contains("object-fit:cover"), "ContentMode Fill -> cover");
    }

    @Test
    void debugRenderEmitsNodeCommentsButDefaultDoesNot() {
        View root = VStack.children(
                dev.pathland.view.Text.make(t -> t.text("Hello")),
                dev.pathland.view.Button.make(b -> b.title("Go").action(() -> { })));
        ProtocolFrame frame = frameOf(root);

        // Default render: no comments.
        assertFalse(renderer().renderFragment(frame, 1).contains("<!--"));

        // Debug render: a per-node comment naming the component + modifiers.
        String debug = renderer().renderFragmentDebug(frame, 1);
        assertTrue(debug.contains("<!-- #1 VStack -->"), "stack comment: " + debug);
        assertTrue(debug.contains("<!-- #2 Text \"Hello\""), "text comment: " + debug);
        // Wrapper model: the button shell has no text of its own (the label is its
        // child node), so the comment names the shell and its modifiers only.
        assertTrue(debug.contains("<!-- #3 Button: eventListeners=0x00000005"),
                "button comment decodes its modifiers: " + debug);
        assertTrue(debug.contains("<!-- #4 Text \"Go\""), "label child comment: " + debug);
    }

    @Test
    void embeddedNativeLibraryIsExtractedAndLoads() {
        // The renderer dylib is embedded in the jar — META-INF/native/<os>-<arch>/
        // in a release build, or the flat META-INF/native/ in a local build — and
        // extracted via JNA. Assert this platform's resource is on the classpath
        // and that instance() links it.
        String lib = HtmlRenderer.embeddedResourcePath();
        assertTrue(lib != null, "renderer dylib is embedded for this platform");
        assertTrue(HtmlRenderer.class.getResourceAsStream(lib) != null,
                "embedded resource is on the classpath: " + lib);
        assertTrue(HtmlRenderer.isAvailable(), "native renderer links (embedded extraction)");
        assertTrue(HtmlRenderer.tryInstance() != null, "tryInstance resolves the renderer");
    }

    @Test
    void servesIconGlyphsFromTheSharedRenderer() {
        // The web renderer owns the canonical ICON_NAME → glyph map in Rust
        // (pathland_html_icon_svg over the C ABI) — hosts serve it at
        // /_pathland/icons/<name>.svg from this shim, so every platform gets the
        // same glyphs from ONE library instead of per-platform static files.
        HtmlRenderer renderer = renderer();

        String home = renderer.iconSvg("home");
        assertTrue(home != null && home.startsWith("<svg class=\"pathland-icon\""),
                "canonical name → the full inline svg shell: " + home);
        assertTrue(home.endsWith("</svg>"), "closed svg: " + home);
        assertTrue(home.length() > 40, "a real glyph path is inlined: " + home);

        // Unknown/extension names yield this renderer's fallback glyph (never null),
        // so a host can always answer with a glyph.
        String unknown = renderer.iconSvg("definitely-not-an-icon");
        assertTrue(unknown != null && unknown.contains("<circle"), "fallback glyph: " + unknown);

        // A null argument is the only null result.
        assertTrue(renderer.iconSvg(null) == null);
    }
}