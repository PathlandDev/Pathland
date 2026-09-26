package com.pathland.render.html;

import com.pathland.view.AccessibilityLabel;
import com.pathland.view.AccessibilityRole;
import com.pathland.view.Audio;
import com.pathland.view.Button;
import com.pathland.view.DatePicker;
import com.pathland.view.DatePickerMode;
import com.pathland.view.Divider;
import com.pathland.view.Environment;
import com.pathland.view.Image;
import com.pathland.view.Picker;
import com.pathland.view.PickerStyle;
import com.pathland.view.ProgressView;
import com.pathland.view.Roles;
import com.pathland.view.ScaledToFill;
import com.pathland.view.Slider;
import com.pathland.view.Text;
import com.pathland.view.TextEditor;
import com.pathland.view.Toggle;
import com.pathland.view.ToggleStyle;
import com.pathland.view.Video;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.emit.Emitter;
import com.pathland.view.emit.Frame;
import com.pathland.view.emit.FrameOpcodeSink;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;
import com.pathland.view.ButtonStyleMod;
import com.pathland.view.Font;

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

    private static Frame frameOf(View root) {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        new Emitter(sink).mount(root, Environment.DEFAULT);
        return sink.frame();
    }

    @Test
    void rendersVStackOfTextAndButton() {
        View root = VStack.of(
                Text.of("Hello Pathland"),
                Button.of("Increment", () -> { }));
        Frame frame = frameOf(root);
        HtmlRenderer renderer = renderer();
        String html = renderer.render(frame, 1);

        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("flex-direction:column"), "stack rendered inline flex");
        assertTrue(html.contains("<link rel=\"stylesheet\" href=\"https://rsms.me/inter/inter.css\">"), "Inter font from the rsms.me CDN");
        assertTrue(html.contains("<span data-pathland-id=\"2\">Hello Pathland</span>"));
        assertTrue(html.contains("<button data-pathland-id=\"3\""), "button keeps its node id");
        assertTrue(html.contains("class=\"pathland-button\""), "button uses the design-system class");
        assertTrue(html.contains(">Increment</button>"));
    }

    @Test
    void rendersFullSnapshotFragment() {
        View root = VStack.of(Text.of("Static"), Text.of(Signals.computed(() -> "Count: 0")));
        HtmlRenderer renderer = renderer();
        String html = renderer.renderFragment(frameOf(root), 1);
        assertTrue(html.contains("Count: 0"));
        assertFalse(html.contains("<!DOCTYPE html>"));
        // renderFullSnapshot is the same stateless fragment render.
        assertTrue(renderer.renderFullSnapshot(frameOf(root), 1).contains("Count: 0"));
    }

    @Test
    void rendersBorderedStyledButton() {
        View root = VStack.of(Button.of("Go", () -> { }))
                .modifier(ButtonStyleMod.of(com.pathland.view.BorderedButtonStyle.INSTANCE));
        String html = renderer().render(frameOf(root), 1);
        assertTrue(html.contains("data-pathland-id=\"2\""), "styled button keeps its node id");
    }

    @Test
    void rendersControls() {
        WritableSignal<Float> value = Signals.signal(0.5f);
        WritableSignal<Boolean> on = Signals.signal(true);
        WritableSignal<Integer> choice = Signals.signal(0);
        View root = VStack.of(
                Toggle.of(ToggleStyle.CHECKBOX, on, "Go"),
                Slider.of(value, 0f, 1f),
                Picker.of(PickerStyle.MENU, choice, Text.of("A"), Text.of("B")),
                ProgressView.of(0.4f),
                Divider.of(),
                TextEditor.of(Signals.signal("hello")),
                DatePicker.of(DatePickerMode.DATE, Signals.signal(20487)));
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
        String html = renderer().renderFragment(frameOf(VStack.of(Text.of("<a & b>"))), 1);
        assertTrue(html.contains("&lt;a &amp; b&gt;"));
    }

    @Test
    void rendersSemanticRoleElements() {
        // A generic stack with a NAVIGATION role renders as a <nav> landmark; the
        // element conveys the role, so no redundant ARIA attribute is emitted.
        View nav = VStack.of(Text.of("Menu"))
                .modifier(AccessibilityRole.of(Roles.NAVIGATION));
        String navHtml = renderer().renderFragment(frameOf(nav), 1);
        assertTrue(navHtml.contains("<nav "), "navigation role -> <nav>");
        assertFalse(navHtml.contains("role=\"navigation\""), "nav conveys the role (no ARIA attr)");

        // A generic Text with a HEADER (heading) role renders as <h2>.
        View heading = Text.of("Title").modifier(AccessibilityRole.of(Roles.HEADER));
        String headingHtml = renderer().renderFragment(frameOf(heading), 1);
        assertTrue(headingHtml.contains("<h2 "), "header role -> <h2>");

        // A heading TEXT_STYLE typography makes a Text a heading even without a
        // role (Font.title2() -> <h3>); a non-heading style stays a <span>.
        View title = Text.of("Sub").font(Font.title2());
        String titleHtml = renderer().renderFragment(frameOf(title), 1);
        assertTrue(titleHtml.contains("<h3 "), "title2 typography -> <h3>");

        View sub = Text.of("Sub").font(Font.subheadline());
        String subHtml = renderer().renderFragment(frameOf(sub), 1);
        assertTrue(subHtml.contains("<span "), "subheadline typography stays a <span>");
    }

    @Test
    void rejectsControlRoles() {
        // Interactive/control roles are NOT roles — they are intrinsic to the
        // control components, so the DSL rejects them.
        assertThrows(IllegalArgumentException.class, () -> AccessibilityRole.of(20), "reserved code rejected");
    }

    @Test
    void rendersMedia() {
        // Video/audio render with renderer-native controls; an image with an
        // accessibility label renders its `alt` and ContentMode Fill → cover.
        View root = VStack.of(
                Video.of("https://example.com/sample.mp4"),
                Audio.of("https://example.com/sample.mp3"),
                Image.of("/_pathland/assets/icons/home.svg")
                        .modifier(AccessibilityLabel.of("Home"))
                        .modifier(ScaledToFill.of())
        );
        String html = renderer().renderFragment(frameOf(root), 1);
        assertTrue(html.contains("<video"), "video element");
        assertTrue(html.contains("controls"), "video native controls");
        assertTrue(html.contains("src=\"https://example.com/sample.mp4\""), "video source");
        assertTrue(html.contains("<audio"), "audio element");
        assertTrue(html.contains("src=\"https://example.com/sample.mp3\""), "audio source");
        assertTrue(html.contains("alt=\"Home\""), "image alt from accessibility label");
        assertTrue(html.contains("object-fit:cover"), "ContentMode Fill -> cover");
    }

    @Test
    void debugRenderEmitsNodeCommentsButDefaultDoesNot() {
        View root = VStack.of(
                com.pathland.view.Text.of("Hello"),
                com.pathland.view.Button.of("Go", () -> { }));
        Frame frame = frameOf(root);

        // Default render: no comments.
        assertFalse(renderer().renderFragment(frame, 1).contains("<!--"));

        // Debug render: a per-node comment naming the component + modifiers.
        String debug = renderer().renderFragmentDebug(frame, 1);
        assertTrue(debug.contains("<!-- #1 VStack -->"), "stack comment: " + debug);
        assertTrue(debug.contains("<!-- #2 Text \"Hello\""), "text comment: " + debug);
        assertTrue(debug.contains("<!-- #3 Button \"Go\": eventListeners=0x00000005"),
                "button comment decodes its modifiers: " + debug);
    }

    @Test
    void embeddedNativeLibraryIsExtractedAndLoads() {
        // The renderer dylib is embedded in the jar (META-INF/native/) and extracted
        // via JNA. Assert the resource is on the classpath (platform extension) and
        // that instance() links it.
        String lib = "/META-INF/native/libpathland_render_html" + platformExtension();
        assertTrue(HtmlRenderer.class.getResourceAsStream(lib) != null,
                "renderer dylib is embedded in the jar resources: " + lib);
        assertTrue(HtmlRenderer.isAvailable(), "native renderer links (embedded extraction)");
        assertTrue(HtmlRenderer.tryInstance() != null, "tryInstance resolves the renderer");
    }

    private static String platformExtension() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac")) {
            return ".dylib";
        }
        if (os.contains("win")) {
            return ".dll";
        }
        return ".so";
    }
}