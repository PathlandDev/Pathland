package dev.pathland.render.html

import dev.pathland.view.core.DatePickerMode
import dev.pathland.view.core.Environment
import dev.pathland.view.core.MediaController
import dev.pathland.view.core.Roles
import dev.pathland.view.core.ToggleStyle
import dev.pathland.view.core.View
import dev.pathland.view.core.transport.FrameOpcodeSink
import dev.pathland.view.core.transport.ProtocolFrame
import dev.pathland.view.dsl.Button
import dev.pathland.view.dsl.DatePicker
import dev.pathland.view.dsl.Divider
import dev.pathland.view.dsl.Font
import dev.pathland.view.dsl.Image
import dev.pathland.view.dsl.Picker
import dev.pathland.view.dsl.PickerStyle
import dev.pathland.view.dsl.ProgressView
import dev.pathland.view.dsl.Slider
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.TextEditor
import dev.pathland.view.dsl.Toggle
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.Video
import dev.pathland.view.dsl.accessibilityLabel
import dev.pathland.view.dsl.accessibilityRole
import dev.pathland.view.dsl.borderedButtonStyle
import dev.pathland.view.dsl.buttonStyle
import dev.pathland.view.dsl.font
import dev.pathland.view.dsl.scaledToFill
import dev.pathland.view.dsl.signal
import dev.pathland.view.engine.Emitter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue

/** DSL -> emitter -> frame -> Rust HtmlRenderer shim (the SSR round-trip). */
class HtmlRendererTest {

    private fun renderer(): HtmlRenderer = HtmlRenderer.instance()

    private fun frameOf(root: View): ProtocolFrame {
        val sink = FrameOpcodeSink()
        Emitter(sink).mount(root, Environment.DEFAULT)
        return sink.frame()
    }

    @Test
    fun rendersVStackOfTextAndButton() {
        val root = VStack {
            Text("Hello Pathland")
            Button("Increment") { }
        }
        val frame = frameOf(root)
        val html = renderer().render(frame, 1)

        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("flex-direction:column"), "stack rendered inline flex")
        assertFalse(html.contains("rsms.me"), "no font CDN link")
        assertFalse(html.contains("<link rel=\"stylesheet\""), "no external stylesheets")
        assertTrue(html.contains("--pl-font-body-family: system-ui, -apple-system"), "system font stack")
        assertTrue(html.contains("<span data-pathland-id=\"2\">Hello Pathland</span>"))
        assertTrue(html.contains("<button data-pathland-id=\"3\""), "button keeps its node id")
        assertTrue(html.contains("class=\"pathland-button\""), "button uses the design-system class")
        assertTrue(html.contains("<span data-pathland-id=\"4\">Increment</span>"),
            "the styled label renders as the button's child")
        assertTrue(html.contains("</button>"))
    }

    @Test
    fun renderLinkedEmitsTheContentHashedStylesheet() {
        val frame = frameOf(Text("Hi"))
        val renderer = renderer()
        val name = renderer.baseCssName()
        assertTrue(name.matches(Regex("base-[0-9a-f]{12}\\.css")), "hashed asset name: $name")
        val css = renderer.baseCss()
        assertFalse(css.isEmpty(), "base CSS bytes are exposed")
        assertTrue(css.contains("--pl-color-primary"), "base design bytes: $css")

        val html = renderer.renderLinked(frame, 1, "/_pathland")
        assertTrue(html.contains("<link rel=\"stylesheet\" href=\"/_pathland/$name\">"),
            "linked stylesheet: $html")
        assertFalse(html.contains("<style>"), "base CSS is not inlined: $html")
        assertTrue(renderer.render(frame, 1).contains("<style>"), "default render remains inline")
    }

    @Test
    fun rendersFullSnapshotFragment() {
        val root = VStack {
            Text("Static")
            Text(dev.pathland.view.dsl.computed { "Count: 0" })
        }
        val renderer = renderer()
        val html = renderer.renderFragment(frameOf(root), 1)
        assertTrue(html.contains("Count: 0"))
        assertFalse(html.contains("<!DOCTYPE html>"))
        assertTrue(renderer.renderFullSnapshot(frameOf(root), 1).contains("Count: 0"))
    }

    @Test
    fun rendersBorderedStyledButton() {
        val root = VStack { Button("Go") { } }.buttonStyle(borderedButtonStyle())
        val html = renderer().render(frameOf(root), 1)
        assertTrue(html.contains("data-pathland-id=\"2\""), "styled button keeps its node id")
    }

    @Test
    fun rendersControls() {
        val value = signal(0.5f)
        val on = signal(true)
        val choice = signal(0)
        val root = VStack {
            Toggle(label = "Go", isOn = on, style = ToggleStyle.CHECKBOX)
            Slider(value, 0f..1f)
            Picker(choice, PickerStyle.MENU) {
                Text("A")
                Text("B")
            }
            ProgressView(0.4f)
            Divider()
            TextEditor(signal("hello"))
            DatePicker(signal(20487), DatePickerMode.DATE)
        }
        val html = renderer().render(frameOf(root), 1)

        assertTrue(html.contains("type=\"checkbox\""), "toggle checkbox")
        assertTrue(html.contains("type=\"range\""), "slider")
        assertTrue(html.contains("value=\"0\" selected>A</option>"), "picker options (first)")
        assertTrue(html.contains("value=\"1\">B</option>"), "picker options (second)")
        assertTrue(html.contains("<progress"), "progress bar")
        assertTrue(html.contains("border-top:"), "divider (border-top div)")
        assertTrue(html.contains("<textarea"), "text editor")
        assertTrue(html.contains("type=\"date\""), "date picker")
    }

    @Test
    fun escapesText() {
        val html = renderer().renderFragment(frameOf(VStack { Text("<a & b>") }), 1)
        assertTrue(html.contains("&lt;a &amp; b&gt;"))
    }

    @Test
    fun rendersSemanticRoleElements() {
        val nav = VStack { Text("Menu") }.accessibilityRole(Roles.NAVIGATION)
        val navHtml = renderer().renderFragment(frameOf(nav), 1)
        assertTrue(navHtml.contains("<nav "), "navigation role -> <nav>")
        assertFalse(navHtml.contains("role=\"navigation\""), "nav conveys the role (no ARIA attr)")

        val heading = Text("Title").accessibilityRole(Roles.HEADER)
        val headingHtml = renderer().renderFragment(frameOf(heading), 1)
        assertTrue(headingHtml.contains("<h2 "), "header role -> <h2>")

        val title = Text("Sub").font(Font.title2())
        val titleHtml = renderer().renderFragment(frameOf(title), 1)
        assertTrue(titleHtml.contains("<h3 "), "title2 typography -> <h3>")

        val sub = Text("Sub").font(Font.subheadline())
        val subHtml = renderer().renderFragment(frameOf(sub), 1)
        assertTrue(subHtml.contains("<span "), "subheadline typography stays a <span>")
    }

    @Test
    fun rejectsControlRoles() {
        assertThrows(IllegalArgumentException::class.java) {
            Text("x").accessibilityRole(20)
        }
    }

    @Test
    fun rendersMedia() {
        val player = MediaController("https://example.com/sample.mp4")
        val root = VStack {
            Video(player)
            Image("/_pathland/assets/icons/home.svg")
                .accessibilityLabel("Home")
                .scaledToFill()
        }
        val html = renderer().renderFragment(frameOf(root), 1)
        assertTrue(html.contains("<video"), "video element")
        assertTrue(html.contains("controls"), "video native controls")
        assertTrue(html.contains("data-pathland-controller"), "video binds its controller")
        assertTrue(html.contains("src=\"https://example.com/sample.mp4\""), "the controller's resource")
        assertTrue(html.contains("alt=\"Home\""), "image alt from accessibility label")
        assertTrue(html.contains("object-fit:cover"), "ContentMode Fill -> cover")
    }

    @Test
    fun debugRenderEmitsNodeCommentsButDefaultDoesNot() {
        val root = VStack {
            Text("Hello")
            Button("Go") { }
        }
        val frame = frameOf(root)
        assertFalse(renderer().renderFragment(frame, 1).contains("<!--"))

        val debug = renderer().renderFragmentDebug(frame, 1)
        assertTrue(debug.contains("<!-- #1 VStack -->"), "stack comment: $debug")
        assertTrue(debug.contains("<!-- #2 Text \"Hello\""), "text comment: $debug")
        assertTrue(debug.contains("<!-- #3 Button: eventListeners=0x00000005"),
            "button comment decodes its modifiers: $debug")
        assertTrue(debug.contains("<!-- #4 Text \"Go\""), "label child comment: $debug")
    }

    @Test
    fun isAlwaysAvailableWithoutNativeLibraries() {
        assertTrue(HtmlRenderer.isAvailable(), "pure-Java renderer is always available")
        assertTrue(HtmlRenderer.tryInstance() != null, "tryInstance resolves the renderer")
    }

    @Test
    fun servesIconGlyphsFromTheSharedRenderer() {
        val renderer = renderer()
        val home = renderer.iconSvg("home")
        assertTrue(home != null && home.startsWith("<svg class=\"pathland-icon\""),
            "canonical name -> the full inline svg shell: $home")
        assertTrue(home!!.endsWith("</svg>"), "closed svg: $home")
        assertTrue(home.length > 40, "a real glyph path is inlined: $home")

        val unknown = renderer.iconSvg("definitely-not-an-icon")
        assertTrue(unknown != null && unknown.contains("<circle"), "fallback glyph: $unknown")
        assertTrue(renderer.iconSvg(null) == null)
    }
}
