package dev.pathland.render.html;

import dev.pathland.view.core.AttributedString;
import dev.pathland.view.core.LinkRun;
import dev.pathland.view.core.StyleRun;
import dev.pathland.view.core.TextAttribute;
import dev.pathland.view.core.protocol.Categories;
import dev.pathland.view.core.protocol.Commands;
import dev.pathland.view.core.protocol.Components;
import dev.pathland.view.core.protocol.Opcode;
import dev.pathland.view.core.protocol.Properties;
import dev.pathland.view.core.protocol.ValueTypes;
import dev.pathland.view.core.transport.ProtocolFrame;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The HTML renderer — a stateless, streaming pure function of the opcode
 * stream, ported from the Rust {@code pathland-render-html} crate.
 *
 * <p>Each call decodes a self-contained snapshot frame into a transient tree,
 * walks it once, and returns HTML. No state is retained between calls, and the
 * renderer never loads a native library, so it runs on every platform and under
 * GraalVM native-image.
 */
public final class HtmlRenderer {

    /** Kept for source compatibility with the former JNA binding. */
    public static final class NativeUnavailableException extends IllegalStateException {
        public NativeUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final String MEDIA_CONTROLLER_SHELL_STYLE =
            "position:absolute;width:1px;height:1px;opacity:0;overflow:hidden;pointer-events:none";

    private static final int APPEND = 0xFFFFFFFF;

    private static final class Holder {
        static final HtmlRenderer INSTANCE = new HtmlRenderer();
    }

    private final boolean debugComments;

    private HtmlRenderer() {
        this(false);
    }

    private HtmlRenderer(boolean debugComments) {
        this.debugComments = debugComments;
    }

    /** The process-wide renderer (stateless). */
    public static HtmlRenderer instance() {
        return Holder.INSTANCE;
    }

    /** Always available — the renderer is pure JVM code. */
    public static HtmlRenderer tryInstance() {
        return Holder.INSTANCE;
    }

    /** True — the renderer is pure JVM code and always linkable. */
    public static boolean isAvailable() {
        return true;
    }

    /** The generated base-design stylesheet bytes. */
    public String baseCss() {
        return BaseCss.builtinCss();
    }

    /** The content-hashed base stylesheet asset name. */
    public String baseCssName() {
        return BaseCss.builtinCssName();
    }

    /** Render a snapshot frame as a complete HTML document (inline base CSS). */
    public String render(ProtocolFrame frame, int root) {
        return renderDocument(frame, root, BaseCss.styleElement(), false);
    }

    /** Render a snapshot frame as a complete HTML document linked to the hashed stylesheet. */
    public String renderLinked(ProtocolFrame frame, int root, String cssBase) {
        String base = cssBase == null ? "" : cssBase.trim();
        if (base.isEmpty() || BaseCss.builtinCssName().isEmpty()) {
            return render(frame, root);
        }
        return renderDocument(frame, root, linkedCssHead(base), false);
    }

    /** {@link #renderLinked} with per-node debug comments. */
    public String renderLinkedDebug(ProtocolFrame frame, int root, String cssBase) {
        String base = cssBase == null ? "" : cssBase.trim();
        if (base.isEmpty() || BaseCss.builtinCssName().isEmpty()) {
            return renderDebug(frame, root);
        }
        return renderDocument(frame, root, linkedCssHead(base), true);
    }

    /** Render a snapshot frame as an HTML fragment (no {@code <html>}). */
    public String renderFragment(ProtocolFrame frame, int root) {
        Decoded decoded = decode(frame);
        return renderNode(decoded.nodes(), root, null, false);
    }

    /** Alias of {@link #renderFragment} for the resync snapshot path. */
    public String renderFullSnapshot(ProtocolFrame frame, int root) {
        return renderFragment(frame, root);
    }

    /** Render a complete HTML document with per-node debug comments. */
    public String renderDebug(ProtocolFrame frame, int root) {
        return renderDocument(frame, root, BaseCss.styleElement(), true);
    }

    /** Render an HTML fragment with per-node debug comments. */
    public String renderFragmentDebug(ProtocolFrame frame, int root) {
        Decoded decoded = decode(frame);
        return renderNode(decoded.nodes(), root, null, true);
    }

    /** The full inline SVG for a canonical icon name; unknown names fall back. */
    public String iconSvg(String name) {
        if (name == null) {
            return null;
        }
        String inner = Icons.webInner(name);
        if (inner == null) {
            inner = Icons.webFallbackSvg();
        }
        return Icons.WEB_SVG_OPEN + inner + Icons.WEB_SVG_CLOSE;
    }

    // ------------------------------------------------------------------
    // Decode
    // ------------------------------------------------------------------

    private record Decoded(TreeMap<Integer, RenderNode> nodes, Tokens tokens, WindowMeta window) {}

    private static Decoded decode(ProtocolFrame frame) {
        TreeMap<Integer, RenderNode> nodes = new TreeMap<>();
        Tokens tokens = new Tokens();
        WindowMeta window = new WindowMeta();
        byte[] strings = frame.strings();
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.TREE) {
                applyTree(nodes, op.command(), op);
            } else if (op.category() == Categories.PARAMETER) {
                if (op.command() == Commands.Parameter.SET_DESIGN_TOKEN) {
                    String path = stringsStr(strings, op.a());
                    if (path != null) {
                        int valueType = op.b() & 0xFF;
                        Tokens.Value value;
                        if (valueType == ValueTypes.STRING) {
                            String str = stringsStr(strings, op.c());
                            value = str != null ? Tokens.Value.ofString(str) : Tokens.Value.fromWire(ValueTypes.U32, 0);
                        } else {
                            value = Tokens.Value.fromWire(valueType, op.c());
                            if (value == null) {
                                value = Tokens.Value.fromWire(ValueTypes.U32, op.c());
                            }
                        }
                        tokens.set(path, valueType, value);
                    }
                } else {
                    applyStyle(nodes, op.command(), op, strings);
                }
            } else if (op.category() == Categories.META) {
                switch (op.command()) {
                    case Commands.Meta.SET_COLOR_SCHEME -> tokens.schemeOverride =
                            op.a() == Commands.ColorSchemeMode.LIGHT ? 1
                                    : op.a() == Commands.ColorSchemeMode.DARK ? 2 : null;
                    case Commands.Meta.RESET_THEME -> tokens.reset();
                    case Commands.Meta.SET_WINDOW_METADATA -> {
                        String value = stringsStr(strings, op.b());
                        if (value != null) {
                            window.set(op.a() & 0xFFFF, value);
                        }
                    }
                    default -> {}
                }
            }
        }
        return new Decoded(nodes, tokens, window);
    }

    private static void applyTree(TreeMap<Integer, RenderNode> nodes, int command, Opcode op) {
        switch (command) {
            case Commands.Tree.CREATE_NODE ->
                    nodes.put(op.a(), new RenderNode(op.b() & 0xFFFF));
            case Commands.Tree.DELETE_NODE -> nodes.remove(op.a());
            case Commands.Tree.INSERT_CHILD -> {
                RenderNode parent = nodes.get(op.a());
                if (parent != null) {
                    int index = op.c();
                    if (index == APPEND) {
                        parent.children.add(op.b());
                    } else {
                        int at = Math.min(index, parent.children.size());
                        parent.children.add(at, op.b());
                    }
                }
            }
            case Commands.Tree.REMOVE_CHILD -> {
                RenderNode parent = nodes.get(op.a());
                if (parent != null) {
                    parent.children.removeIf(child -> child == op.b());
                }
            }
            case Commands.Tree.MOVE_CHILD -> {
                RenderNode parent = nodes.get(op.a());
                if (parent != null) {
                    parent.children.removeIf(child -> child == op.b());
                    int at = Math.min(op.c(), parent.children.size());
                    parent.children.add(at, op.b());
                }
            }
            default -> {}
        }
    }

    private static void applyStyle(TreeMap<Integer, RenderNode> nodes, int command, Opcode op,
            byte[] strings) {
        switch (command) {
            case Commands.Parameter.SET_TEXT -> {
                String text = stringsStr(strings, op.b());
                if (text != null) {
                    RenderNode node = nodes.get(op.a());
                    if (node != null) {
                        node.text = text;
                        node.attributed = null;
                    }
                }
            }
            case Commands.Parameter.SET_ATTRIBUTED_TEXT -> {
                byte[] bytes = stringsBytes(strings, op.b());
                if (bytes != null) {
                    try {
                        AttributedString value = AttributedString.decode(bytes);
                        RenderNode node = nodes.get(op.a());
                        if (node != null) {
                            node.text = value.getText();
                            node.attributed = value;
                        }
                    } catch (RuntimeException ignored) {
                        // malformed payload: leave the node unchanged
                    }
                }
            }
            case Commands.Parameter.SET_PROPERTY -> {
                int property = op.b() & 0xFFFF;
                RenderNode node = nodes.get(op.a());
                if (node != null) {
                    int valueType = (op.b() >>> 16) & 0xFF;
                    if (valueType == ValueTypes.STRING) {
                        String text = stringsStr(strings, op.c());
                        if (text != null) {
                            node.strings.put(property, text);
                        }
                    } else if (valueType == ValueTypes.DESIGN_TOKEN) {
                        String path = stringsStr(strings, op.c());
                        if (path != null) {
                            node.tokenRefs.put(property, path);
                        }
                    } else if (valueType == ValueTypes.LIST) {
                        String csv = listCsv(strings, op.c());
                        if (csv != null) {
                            node.strings.put(property, csv);
                        }
                        List<Float> values = listF32(strings, op.c());
                        if (values != null) {
                            node.fitThresholds.clear();
                            node.fitThresholds.addAll(values);
                        }
                    } else {
                        node.properties.put(property, op.c());
                    }
                }
            }
            case Commands.Parameter.SET_DATE -> {
                RenderNode node = nodes.get(op.a());
                if (node != null) {
                    node.date = new RenderNode.DateValue(op.b(), op.c());
                }
            }
            default -> {}
        }
    }

    private static String stringsStr(byte[] strings, int offset) {
        byte[] bytes = stringsBytes(strings, offset);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] stringsBytes(byte[] strings, int offset) {
        if (offset < 0 || (long) offset + 4 > strings.length) {
            return null;
        }
        int length = readIntLE(strings, offset);
        long start = (long) offset + 4;
        if (length < 0 || start + length > strings.length) {
            return null;
        }
        byte[] out = new byte[length];
        System.arraycopy(strings, (int) start, out, 0, length);
        return out;
    }

    private static String listCsv(byte[] strings, int offset) {
        if (offset < 0 || (long) offset + 4 > strings.length) {
            return null;
        }
        int count = readIntLE(strings, offset);
        long start = (long) offset + 4;
        if (count < 0 || start + (long) count * 4 > strings.length) {
            return null;
        }
        List<String> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int bits = readIntLE(strings, (int) (start + (long) i * 4));
            float value = Float.intBitsToFloat(bits);
            if (value == Math.rint(value)) {
                parts.add(String.format(java.util.Locale.ROOT, "%.0f", value));
            } else {
                parts.add(String.format(java.util.Locale.ROOT, "%.1f", value));
            }
        }
        return String.join(",", parts);
    }

    private static List<Float> listF32(byte[] strings, int offset) {
        if (offset < 0 || (long) offset + 4 > strings.length) {
            return null;
        }
        int count = readIntLE(strings, offset);
        long start = (long) offset + 4;
        if (count < 0 || start + (long) count * 4 > strings.length) {
            return null;
        }
        List<Float> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(Float.intBitsToFloat(readIntLE(strings, (int) (start + (long) i * 4))));
        }
        return values;
    }

    private static int readIntLE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }

    // ------------------------------------------------------------------
    // Document
    // ------------------------------------------------------------------

    private String renderDocument(ProtocolFrame frame, int root, String cssHead, boolean debug) {
        Decoded decoded = decode(frame);
        TreeMap<Integer, RenderNode> nodes = decoded.nodes();
        String body = renderNode(nodes, root, null, debug);
        String lang = Format.escapeHtmlAttr(decoded.window().locale == null ? "en" : decoded.window().locale);
        String title = Format.escape(decoded.window().title == null ? "Pathland" : decoded.window().title);
        String description = decoded.window().description == null
                ? ""
                : "<meta name=\"description\" content=\""
                        + Format.escapeHtmlAttr(decoded.window().description) + "\">\n";
        String tokenRules = decoded.tokens().css();
        String tokenStyle = tokenRules.isEmpty()
                ? ""
                : "<style data-pathland-tokens>" + tokenRules + "</style>\n";
        String fitRules = fitBandStyles(nodes);
        String fitStyle = fitRules.isEmpty()
                ? ""
                : "<style data-pathland-fit>" + fitRules + "</style>\n";
        String schemeAttr = switch (decoded.tokens().schemeOverride == null ? 0 : decoded.tokens().schemeOverride) {
            case 1 -> " data-pathland-scheme=\"light\"";
            case 2 -> " data-pathland-scheme=\"dark\"";
            default -> "";
        };
        return "<!DOCTYPE html>\n<html lang=\"" + lang + "\"" + schemeAttr + ">\n<head>\n"
                + "<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "<title>" + title + "</title>\n"
                + description + cssHead + tokenStyle + fitStyle + "\n</head>\n<body>" + body
                + "</body>\n</html>\n";
    }

    private static String linkedCssHead(String cssBase) {
        String base = cssBase;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return "<link rel=\"stylesheet\" href=\"" + Format.escapeHtmlAttr(base) + "/"
                + BaseCss.builtinCssName() + "\">";
    }

    private static String fitBandStyles(TreeMap<Integer, RenderNode> nodes) {
        StringBuilder rules = new StringBuilder();
        for (var entry : nodes.entrySet()) {
            int id = entry.getKey();
            RenderNode node = entry.getValue();
            if (node.component != Components.SIZE_THAT_FITS || !node.fitCandidatesValid()) {
                continue;
            }
            int n = node.children.size();
            float seen = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < node.fitThresholds.size(); i++) {
                float threshold = node.fitThresholds.get(i);
                if (i == 0) {
                    seen = threshold;
                    continue;
                }
                if (threshold <= seen) {
                    continue;
                }
                seen = threshold;
                StringBuilder band = new StringBuilder("@container pf-").append(id)
                        .append(" (min-width: ").append(Format.f32(threshold)).append("px) {");
                for (int j = 0; j < n; j++) {
                    if (j == i) {
                        continue;
                    }
                    band.append(" [data-pathland-id=\"").append(id)
                            .append("\"] > [data-pathland-fit-cand=\"").append(j)
                            .append("\"] { display: none !important; }");
                }
                band.append(" [data-pathland-id=\"").append(id)
                        .append("\"] > [data-pathland-fit-cand=\"").append(i)
                        .append("\"] { display: contents !important; } } \n");
                rules.append(band);
            }
        }
        return rules.toString();
    }

    // ------------------------------------------------------------------
    // Node rendering
    // ------------------------------------------------------------------

    private String renderNode(TreeMap<Integer, RenderNode> nodes, int id, Integer parentComponent,
            boolean debug) {
        RenderNode node = nodes.get(id);
        if (node == null) {
            return "";
        }
        StringBuilder childrenBuilder = new StringBuilder();
        for (int child : node.children) {
            childrenBuilder.append(renderNode(nodes, child, node.component, debug));
        }
        String children = childrenBuilder.toString();

        StringBuilder dataId = new StringBuilder(" data-pathland-id=\"").append(id).append('"');
        dataId.append(slotAttrs(node));

        StringBuilder cssBuilder = new StringBuilder(node.styleCss());
        cssBuilder.append(node.borderStyle());
        String css = cssBuilder.toString();

        StringBuilder derived = new StringBuilder();
        if (parentComponent != null) {
            Boolean mainHorizontal = RenderNode.stackMainHorizontal(parentComponent);
            if (mainHorizontal != null
                    && RenderNode.fillsAxis(nodes, id, !mainHorizontal, false)) {
                derived.append("align-self:stretch;");
            }
        }
        int lineLimit = node.u32Property(Properties.LINE_LIMIT, 0);
        if (lineLimit > 0) {
            css = css + "display:-webkit-box;overflow:hidden;-webkit-line-clamp:" + lineLimit
                    + ";-webkit-box-orient:vertical;";
        }
        String style = Format.styleAttr(css + derived);
        String event = eventAttrs(node);
        String aria = ariaAttrs(node);
        RoleSpec.ShellKind kind = RoleSpec.shellKind(node.component);
        String semantic = RoleSpec.semanticTag(kind, node.roleCode());

        String element;
        switch (node.component) {
            case Components.VSTACK -> element = wrapStack(id, "column", semantic, node, children,
                    css + RenderNode.fillPropagation(nodes, id, node, false) + derived, event, aria);
            case Components.HSTACK -> element = wrapStack(id, "row", semantic, node, children,
                    css + RenderNode.fillPropagation(nodes, id, node, true) + derived, event, aria);
            case Components.LAZY_VSTACK -> element = wrapStack(id, "column", semantic, node, children,
                    css + RenderNode.fillPropagation(nodes, id, node, false) + derived, event, aria);
            case Components.LAZY_HSTACK -> element = wrapStack(id, "row", semantic, node, children,
                    css + RenderNode.fillPropagation(nodes, id, node, true) + derived, event, aria);
            case Components.PROXY_VIEW -> element = wrapStack(id, "column", semantic, node, children,
                    css + RenderNode.fillPropagation(nodes, id, node, false) + derived, event, aria);
            case Components.SIZE_THAT_FITS -> element = renderSizeThatFits(nodes, id, node, children,
                    dataId.toString(), css, derived.toString(), event, aria, debug);
            case Components.TEXT -> {
                String tag = RoleSpec.textTag(kind, node.roleCode(), node.textStyleCode());
                if (tag == null) {
                    tag = "span";
                }
                String content = node.attributed != null
                        ? renderAttributed(node.attributed)
                        : Format.escape(node.text == null ? "" : node.text);
                element = "<" + tag + dataId + event + aria + style + ">" + content + "</" + tag + ">";
            }
            case Components.ICON -> {
                StringBuilder iconName = new StringBuilder();
                String name = node.strings.get(Properties.ICON_NAME);
                if (name != null) {
                    iconName.append(" data-pathland-icon=\"").append(Format.escape(name)).append('"');
                }
                String inner = name == null ? null : Icons.webInner(name);
                if (inner == null) {
                    inner = Icons.webFallbackSvg();
                }
                String iconAria;
                String label = node.strings.get(Properties.LABEL);
                if (label != null && !label.isEmpty()) {
                    iconAria = " role=\"img\" aria-label=\"" + Format.escape(label) + "\"";
                } else {
                    iconAria = " aria-hidden=\"true\" focusable=\"false\"";
                }
                String open = Icons.WEB_SVG_OPEN.substring(0, Icons.WEB_SVG_OPEN.length() - 1);
                element = open + dataId + iconName + iconAria + event + style + ">" + inner
                        + "</svg>";
            }
            case Components.BUTTON -> {
                String body = node.children.isEmpty()
                        ? Format.escape(node.text == null ? "" : node.text)
                        : children;
                element = "<button" + dataId + event + aria + Format.classAttr("pathland-button")
                        + style + ">" + body + "</button>";
            }
            case Components.TOGGLE -> {
                int toggleStyle = (int) Math.round(node.f32Property(Properties.TOGGLE_STYLE, 0.0f));
                String checked = node.checked() ? " checked" : "";
                String body = node.children.isEmpty()
                        ? "<span class=\"pathland-text\">"
                                + Format.escape(node.text == null ? "" : node.text) + "</span>"
                        : "<div class=\"pathland-custom-body\">" + children + "</div>";
                String toggleClass = Format.classAttr("pathland-toggle");
                if (toggleStyle == 1) {
                    element = "<label" + dataId + event + aria + toggleClass + style
                            + "><input type=\"checkbox\"" + checked + ">" + body + "</label>";
                } else if (toggleStyle == 2) {
                    String pressed = node.checked() ? "true" : "false";
                    element = "<button" + dataId + event + aria + " type=\"button\" aria-pressed=\""
                            + pressed + "\"" + style + ">" + body + "</button>";
                } else {
                    element = "<label" + dataId + event + aria + toggleClass + style
                            + "><input type=\"checkbox\" role=\"switch\"" + checked + ">" + body
                            + "</label>";
                }
            }
            case Components.SPACER -> {
                String tag = semantic == null ? "div" : semantic;
                String spacerStyle = Format.styleAttr("flex:1;" + css);
                element = "<" + tag + dataId + event + aria + spacerStyle + "></" + tag + ">";
            }
            case Components.SLIDER -> {
                float min = node.f32Property(Properties.MIN_VALUE, 0.0f);
                float max = node.f32Property(Properties.MAX_VALUE, 1.0f);
                float value = node.f32Property(Properties.VALUE, min);
                String body = node.children.isEmpty()
                        ? "<span class=\"pathland-text\">"
                                + Format.escape(node.text == null ? "" : node.text) + "</span>"
                        : "<div class=\"pathland-custom-body\">" + children + "</div>";
                int fill = max > min
                        ? Math.round(Math.max(0.0f, Math.min(100.0f, ((value - min) / (max - min)) * 100.0f)))
                        : 0;
                String sliderClass = Format.classAttr("pathland-slider");
                String label = node.strings.get(Properties.LABEL);
                String inputAria = label != null && !label.isEmpty()
                        ? " aria-label=\"" + Format.escapeHtmlAttr(label) + "\""
                        : "";
                element = "<label" + dataId + event + aria + sliderClass + style
                        + "><input type=\"range\" min=\"" + Format.f32(min) + "\" max=\""
                        + Format.f32(max) + "\" step=\"any\" value=\"" + Format.f32(value) + "\""
                        + inputAria + " style=\"--pl-slider-fill:" + fill + "%\">" + body
                        + "</label>";
            }
            case Components.TEXT_FIELD -> {
                String value = Format.escape(node.text == null ? "" : node.text);
                String label = Format.escape(node.strings.getOrDefault(Properties.LABEL, ""));
                String prompt = Format.escape(node.strings.getOrDefault(Properties.PROMPT, ""));
                String inputType = node.f32Property(Properties.IS_SECURE, 0.0f) != 0.0f
                        ? "password" : "text";
                String textFieldClass = Format.classAttr("pathland-textfield");
                element = "<label" + dataId + event + aria + textFieldClass + style
                        + "><span class=\"pathland-label\">" + label
                        + "</span><input class=\"pathland-input pathland-control\" type=\""
                        + inputType + "\" value=\"" + value + "\" placeholder=\"" + prompt
                        + "\"></label>";
            }
            case Components.TEXT_EDITOR -> {
                String value = Format.escape(node.text == null ? "" : node.text);
                element = "<textarea" + dataId + event + aria
                        + " class=\"pathland-input pathland-control\" rows=\"4\"" + style + ">"
                        + value + "</textarea>";
            }
            case Components.IMAGE -> {
                String src = Format.escape(node.strings.getOrDefault(Properties.IMAGE_SOURCE, ""));
                String alt = Format.escape(node.strings.getOrDefault(Properties.LABEL, ""));
                element = "<img" + dataId + event + aria + " src=\"" + src + "\" alt=\"" + alt
                        + "\"" + style + ">";
            }
            case Components.MEDIA_CONTROLLER -> {
                String src = Format.escape(node.strings.getOrDefault(Properties.MEDIA_SOURCE, ""));
                String media = src.isEmpty() ? "" : "<video data-pathland-media src=\"" + src + "\"></video>";
                element = "<div" + dataId + " aria-hidden=\"true\" style=\""
                        + MEDIA_CONTROLLER_SHELL_STYLE + "\">" + media + "</div>";
            }
            case Components.VIDEO -> {
                int controller = node.u32Property(Properties.MEDIA_CONTROLLER_ID, 0);
                String binding = controller != 0
                        ? " data-pathland-controller=\"" + Integer.toUnsignedString(controller) + "\""
                        : "";
                if (node.children.isEmpty()) {
                    element = "<video" + dataId + event + aria + " class=\"pathland-video\""
                            + binding + " controls" + style + "></video>";
                } else {
                    element = "<div" + dataId + event + aria + " class=\"pathland-media\"" + binding
                            + style + ">" + children + "</div>";
                }
            }
            case Components.COLOR -> {
                String tag = semantic == null ? "div" : semantic;
                int color = node.u32Property(Properties.COLOR, 0xFF000000);
                element = "<" + tag + dataId + event + aria
                        + " style=\"flex:1 1 auto;align-self:stretch;background-color:"
                        + Format.rgba(color) + ";" + css + "\">" + children + "</" + tag + ">";
            }
            case Components.SHAPE -> {
                int shapeKind = (int) Math.round(node.f32Property(Properties.SHAPE_KIND, 0.0f));
                int fill = node.u32Property(Properties.COLOR, 0xFF000000);
                float w = node.f32Property(Properties.WIDTH, 100.0f);
                float h = node.f32Property(Properties.HEIGHT, 100.0f);
                String base = "width:" + RenderNode.sizeCss(w) + ";height:" + RenderNode.sizeCss(h)
                        + ";background-color:" + Format.rgba(fill) + ";" + css;
                String tag = semantic == null ? "div" : semantic;
                element = switch (shapeKind) {
                    case 0, 4 -> "<" + tag + dataId + event + aria + " style=\"" + base
                            + "border-radius:50%;\"></" + tag + ">";
                    case 2 -> "<" + tag + dataId + event + aria + " style=\"" + base
                            + "border-radius:" + Format.f32(node.f32Property(Properties.BORDER_RADIUS, 8.0f))
                            + "px;\"></" + tag + ">";
                    case 3 -> "<" + tag + dataId + event + aria + " style=\"" + base
                            + "border-radius:9999px;\"></" + tag + ">";
                    case 5 -> "<svg" + dataId + event + aria
                            + " width=\"100\" height=\"100\" viewBox=\"0 0 100 100\"><rect width=\"100\" height=\"100\" fill=\""
                            + Format.rgba(fill) + "\"/></svg>";
                    default -> "<" + tag + dataId + event + aria + " style=\"" + base + "\"></"
                            + tag + ">";
                };
            }
            case Components.DIVIDER -> {
                float width = node.f32Property(Properties.BORDER_WIDTH, 1.0f);
                Integer colorBits = node.properties.get(Properties.COLOR);
                String color = colorBits == null ? "rgba(0,0,0,0.2)" : Format.rgba(colorBits);
                element = "<div" + dataId + event + aria + " style=\"height:0;width:100%;border-top:"
                        + Format.f32(width) + "px solid " + color + ";" + derived + "\"></div>";
            }
            case Components.PROGRESS_VIEW -> {
                boolean indeterminate = node.f32Property(Properties.IS_INDETERMINATE, 0.0f) != 0.0f
                        || node.f32Property(Properties.PROGRESS, -1.0f) < 0.0f;
                if (indeterminate) {
                    element = "<div" + dataId + Format.classAttr("pathland-spinner") + style
                            + "></div>";
                } else {
                    float value = node.f32Property(Properties.PROGRESS, 0.0f);
                    float max = node.f32Property(Properties.MAX_VALUE, 1.0f);
                    element = "<progress" + dataId + event + aria
                            + " class=\"pathland-progress\" value=\"" + Format.f32(value)
                            + "\" max=\"" + Format.f32(max) + "\"" + style + "></progress>";
                }
            }
            case Components.GAUGE -> {
                float min = node.f32Property(Properties.MIN_VALUE, 0.0f);
                float max = node.f32Property(Properties.MAX_VALUE, 1.0f);
                float value = node.f32Property(Properties.VALUE, min);
                float pct = max > min
                        ? Math.max(0.0f, Math.min(1.0f, (value - min) / (max - min))) * 100.0f
                        : 0.0f;
                element = "<div" + dataId + event + aria + Format.classAttr("pathland-gauge")
                        + " data-min=\"" + Format.f32(min) + "\" data-max=\"" + Format.f32(max)
                        + "\"" + style + "><div style=\"width:" + Format.f32(pct)
                        + "%\"></div></div>";
            }
            case Components.GRID, Components.LAZY_VGRID, Components.LAZY_HGRID ->
                    element = renderGrid(nodes, id, node, dataId.toString(), css, event, aria,
                            semantic, debug);
            case Components.SCROLLVIEW -> {
                String tag = semantic == null ? "div" : semantic;
                String scrollStyle = Format.styleAttr("flex:1 1 auto;align-self:stretch;overflow:auto;" + css);
                String content = node.children.isEmpty()
                        ? ""
                        : renderNode(nodes, node.children.get(0), node.component, debug);
                element = "<" + tag + dataId + event + aria + scrollStyle + ">" + content + "</"
                        + tag + ">";
            }
            case Components.ZSTACK -> element = renderZStack(nodes, id, node, dataId.toString(),
                    css, derived.toString(), event, aria, semantic, debug);
            case Components.STEPPER -> {
                float min = node.f32Property(Properties.MIN_VALUE, 0.0f);
                float max = node.f32Property(Properties.MAX_VALUE, 100.0f);
                float value = node.f32Property(Properties.VALUE, min);
                float step = node.f32Property(Properties.STEP_VALUE, 1.0f);
                String display = Format.f32(Math.round(value * 100.0f) / 100.0f);
                element = "<div" + dataId + event + aria + Format.classAttr("pathland-stepper")
                        + style + "><button type=\"button\" data-step=\"-1\">−</button><span>"
                        + display
                        + "</span><button type=\"button\" data-step=\"1\">+</button><span class=\"pathland-stepper-range\" hidden data-min=\""
                        + Format.f32(min) + "\" data-max=\"" + Format.f32(max) + "\" data-step=\""
                        + Format.f32(step) + "\"></span></div>";
            }
            case Components.DATE_PICKER -> {
                int mode = (int) Math.round(node.f32Property(Properties.DATE_PICKER_MODE, 0.0f));
                String type = "date";
                String value = "";
                if (node.date != null) {
                    int days = node.date.days();
                    int millis = node.date.millis();
                    if (mode == 1) {
                        int[] hms = millisToHms(millis);
                        type = "time";
                        value = String.format(java.util.Locale.ROOT, "%02d:%02d", hms[0], hms[1]);
                    } else if (mode == 2) {
                        int[] date = daysToDate(days);
                        int[] hms = millisToHms(millis);
                        type = "datetime-local";
                        value = String.format(java.util.Locale.ROOT, "%04d-%02d-%02dT%02d:%02d",
                                date[0], date[1], date[2], hms[0], hms[1]);
                    } else {
                        int[] date = daysToDate(days);
                        value = String.format(java.util.Locale.ROOT, "%04d-%02d-%02d",
                                date[0], date[1], date[2]);
                    }
                }
                element = "<input" + dataId + event + aria
                        + " class=\"pathland-date pathland-control\" type=\"" + type
                        + "\" value=\"" + value + "\"" + style + ">";
            }
            case Components.PICKER -> {
                int selected = node.u32Property(Properties.SELECTION, 0);
                StringBuilder options = new StringBuilder();
                for (int i = 0; i < node.children.size(); i++) {
                    int child = node.children.get(i);
                    RenderNode childNode = nodes.get(child);
                    String label = childNode == null || childNode.text == null ? "" : childNode.text;
                    String selectedAttr = i == selected ? " selected" : "";
                    options.append("<option data-pathland-id=\"").append(child).append("\" value=\"")
                            .append(i).append('"').append(selectedAttr).append('>')
                            .append(Format.escape(label)).append("</option>");
                }
                element = "<select" + dataId + event + aria
                        + " class=\"pathland-select pathland-control\"" + style + ">" + options
                        + "</select>";
            }
            case Components.MENU -> {
                String label = Format.escape(node.text == null ? "" : node.text);
                element = "<div" + dataId + event + aria + Format.classAttr("pathland-menu")
                        + " role=\"menu\"" + style + "><div class=\"pathland-menu-trigger\">" + label
                        + "</div><div class=\"pathland-menu-items\">" + children + "</div></div>";
            }
            case Components.COLOR_PICKER -> {
                int color = node.u32Property(Properties.COLOR_VALUE, 0xFF000000);
                String hex = String.format(java.util.Locale.ROOT, "#%02x%02x%02x",
                        (color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF);
                element = "<input" + dataId + event + aria
                        + " class=\"pathland-color pathland-control\" type=\"color\" value=\""
                        + hex + "\"" + style + ">";
            }
            case Components.COMMENT -> element = "";
            default -> element = "";
        }
        if (debug) {
            return RenderDebug.nodeComment(id, node) + element;
        }
        return element;
    }

    private String renderSizeThatFits(TreeMap<Integer, RenderNode> nodes, int id, RenderNode node,
            String children, String dataId, String css, String derived, String event, String aria,
            boolean debug) {
        String fitAttr = "";
        String csv = node.strings.get(Properties.FIT_QUERY);
        if (csv != null) {
            fitAttr = " data-pathland-fit=\"" + Format.escape(csv) + "\"";
        }
        if (node.fitCandidatesValid()) {
            String fitCands = " data-pathland-fit-cands=\"" + node.children.size() + "\"";
            StringBuilder boxed = new StringBuilder();
            for (int i = 0; i < node.children.size(); i++) {
                int child = node.children.get(i);
                String inner = renderNode(nodes, child, node.component, debug);
                String display = i == 0 ? "display:contents" : "display:none";
                boxed.append("<div data-pathland-fit-cand=\"").append(i)
                        .append("\" data-pathland-fit-cand-id=\"").append(child)
                        .append("\" style=\"").append(display).append("\">").append(inner)
                        .append("</div>");
            }
            String slotBase = "display:flex;flex-direction:inherit;align-items:inherit;justify-content:inherit;flex:1 1 auto;align-self:stretch;min-width:0;min-height:0;container-type:inline-size;container-name:pf-"
                    + id + ";" + css + derived;
            return "<div" + dataId + " class=\"pathland-ftf\"" + fitAttr + fitCands + event + aria
                    + " style=\"" + slotBase + "\">" + boxed + "</div>";
        } else if (node.children.size() > 1) {
            String only = renderNode(nodes, node.children.get(0), node.component, debug);
            return "<div" + dataId + " class=\"pathland-ftf\"" + fitAttr + event + aria
                    + " style=\"display:flex;flex-direction:inherit;align-items:inherit;justify-content:inherit;flex:1 1 auto;align-self:stretch;min-width:0;min-height:0;"
                    + css + derived + "\">" + only + "</div>";
        }
        return "<div" + dataId + " class=\"pathland-ftf\"" + fitAttr + event + aria
                + " style=\"display:flex;flex-direction:inherit;align-items:inherit;justify-content:inherit;flex:1 1 auto;align-self:stretch;min-width:0;min-height:0;"
                + css + derived + "\">" + children + "</div>";
    }

    private String renderGrid(TreeMap<Integer, RenderNode> nodes, int id, RenderNode node,
            String dataId, String css, String event, String aria, String semantic, boolean debug) {
        String tag = semantic == null ? "div" : semantic;
        boolean explicitRows = false;
        for (int child : node.children) {
            RenderNode childNode = nodes.get(child);
            if (childNode != null && childNode.component == Components.GRID_ROW) {
                explicitRows = true;
                break;
            }
        }
        List<String> tracks = gridTracks(node);
        Integer trackCount = tracks == null ? null : tracks.size();
        Integer baseColumns = RenderNode.gridCount(node, Properties.GRID_COLUMNS);
        Integer effectiveColumns;
        if (trackCount != null) {
            effectiveColumns = trackCount;
        } else if (explicitRows) {
            int width = baseColumns == null ? 0 : baseColumns;
            int run = 0;
            for (int child : node.children) {
                RenderNode childNode = nodes.get(child);
                if (childNode != null && childNode.component == Components.GRID_ROW) {
                    width = Math.max(width, childNode.children.size());
                    run = 0;
                } else {
                    run++;
                    width = Math.max(width, run);
                }
            }
            effectiveColumns = baseColumns != null ? baseColumns : Math.max(1, width);
        } else {
            effectiveColumns = baseColumns;
        }
        String gridCss = gridStyle(node, effectiveColumns);
        String gridStyle = Format.styleAttr(gridCss + css);
        int align = (int) node.f32Property(Properties.ALIGNMENT, 0.0f);

        StringBuilder inner = new StringBuilder();
        if (explicitRows) {
            int columns = effectiveColumns == null ? 1 : effectiveColumns;
            int row = 0;
            int col = 0;
            for (int child : node.children) {
                RenderNode childNode = nodes.get(child);
                if (childNode == null) {
                    continue;
                }
                if (childNode.component == Components.GRID_ROW) {
                    if (col > 0) {
                        row++;
                    }
                    col = 0;
                    StringBuilder cells = new StringBuilder();
                    for (int cell : childNode.children) {
                        String cellHtml = renderNode(nodes, cell, node.component, debug);
                        if (!cellHtml.isEmpty()) {
                            cells.append(cellShell(nodes, cellHtml, row, col, cell, true, align));
                        }
                        col++;
                    }
                    inner.append("<div data-pathland-id=\"").append(child)
                            .append("\" style=\"display:contents\">").append(cells).append("</div>");
                    row++;
                    col = 0;
                } else {
                    if (col >= columns) {
                        row++;
                        col = 0;
                    }
                    String cellHtml = renderNode(nodes, child, node.component, debug);
                    if (!cellHtml.isEmpty()) {
                        inner.append(cellShell(nodes, cellHtml, row, col, child, true, align));
                        col++;
                    }
                }
            }
        } else {
            for (int child : node.children) {
                String cellHtml = renderNode(nodes, child, node.component, debug);
                if (!cellHtml.isEmpty()) {
                    inner.append(cellShell(nodes, cellHtml, 0, 0, child, false, align));
                }
            }
        }
        return "<" + tag + dataId + event + aria + gridStyle + ">" + inner + "</" + tag + ">";
    }

    private static String cellShell(TreeMap<Integer, RenderNode> nodes, String cellHtml, int row,
            int col, int cell, boolean explicitRows, int align) {
        String placement = explicitRows ? "grid-row:" + (row + 1) + ";grid-column:" + (col + 1) + ";" : "";
        RenderNode cellNode = nodes.get(cell);
        String z = "";
        if (cellNode != null) {
            float zIndex = cellNode.f32Property(Properties.Z_INDEX, 0.0f);
            if (zIndex != 0.0f) {
                z = "z-index:" + (int) zIndex + ";";
            }
        }
        String justify = RenderNode.fillsAxis(nodes, cell, true, false)
                ? "stretch" : RenderNode.alignH(align);
        String alignment = RenderNode.fillsAxis(nodes, cell, false, false)
                ? "stretch" : RenderNode.alignV(align);
        return "<div style=\"" + placement + z + "justify-self:" + justify + ";align-self:"
                + alignment + ";\">" + cellHtml + "</div>";
    }

    private String renderZStack(TreeMap<Integer, RenderNode> nodes, int id, RenderNode node,
            String dataId, String css, String derived, String event, String aria, String semantic,
            boolean debug) {
        String tag = semantic == null ? "div" : semantic;
        int align = (int) node.f32Property(Properties.ALIGNMENT, 0.0f);
        StringBuilder inner = new StringBuilder();
        for (int child : node.children) {
            String childHtml = renderNode(nodes, child, node.component, debug);
            if (childHtml.isEmpty()) {
                continue;
            }
            RenderNode childNode = nodes.get(child);
            int z = childNode == null ? 0 : (int) childNode.f32Property(Properties.Z_INDEX, 0.0f);
            String justify = RenderNode.fillsAxis(nodes, child, true, false)
                    ? "stretch" : RenderNode.alignH(align);
            String alignment = RenderNode.fillsAxis(nodes, child, false, false)
                    ? "stretch" : RenderNode.alignV(align);
            inner.append("<div style=\"grid-area:1/1;z-index:").append(z)
                    .append(";justify-self:").append(justify).append(";align-self:")
                    .append(alignment).append(";\">").append(childHtml).append("</div>");
        }
        StringBuilder zcss = new StringBuilder("display:grid;isolation:isolate;"
                + "grid-template-columns:minmax(0,1fr);grid-template-rows:minmax(0,1fr);"
                + "justify-items:" + RenderNode.alignH(align) + ";align-items:"
                + RenderNode.alignV(align) + ";");
        int[] props = {Properties.WIDTH, Properties.HEIGHT};
        String[] hugCss = {"width:max-content;", "height:max-content;"};
        String[] fillCss = {"width:100%;", "height:100%;"};
        boolean[] axes = {true, false};
        for (int i = 0; i < axes.length; i++) {
            Float hint = node.f32(props[i]);
            boolean hug = hint == null || hint == RenderNode.HUG_CONTENT;
            if (hug) {
                zcss.append(RenderNode.fillsAxis(nodes, id, axes[i], false) ? fillCss[i] : hugCss[i]);
            }
        }
        String zstyle = Format.styleAttr(zcss + css + derived);
        return "<" + tag + dataId + event + aria + zstyle + ">" + inner + "</" + tag + ">";
    }

    private static String wrapStack(int id, String direction, String semantic, RenderNode node,
            String children, String css, String event, String aria) {
        String tag = semantic == null ? "div" : semantic;
        Integer bits = node.properties.get(Properties.ALIGNMENT);
        int alignment = bits == null ? 3 : ((int) Float.intBitsToFloat(bits)) & 0xFF;
        String align = switch (alignment) {
            case 0 -> "flex-start";
            case 1 -> "center";
            case 2 -> "flex-end";
            default -> "flex-start";
        };
        String stackStyle = Format.styleAttr("display:flex;flex-direction:" + direction
                + ";align-items:" + align + ";" + css);
        return "<" + tag + " data-pathland-id=\"" + id + "\"" + slotAttrs(node) + event + aria
                + stackStyle + ">" + children + "</" + tag + ">";
    }

    private static String gridStyle(RenderNode node, Integer columns) {
        int align = (int) node.f32Property(Properties.ALIGNMENT, 0.0f);
        StringBuilder css = new StringBuilder("display:grid;justify-items:")
                .append(RenderNode.alignH(align)).append(";align-items:")
                .append(RenderNode.alignV(align)).append(';');
        String tracksCss = gridTracksCss(node);
        switch (node.component) {
            case Components.GRID -> {
                if (tracksCss != null) {
                    css.append("grid-template-columns:").append(tracksCss).append(';');
                } else if (columns != null) {
                    css.append("grid-template-columns:repeat(").append(columns)
                            .append(",minmax(0,1fr));");
                }
                Integer rows = RenderNode.gridCount(node, Properties.GRID_ROWS);
                if (rows != null) {
                    css.append("grid-template-rows:repeat(").append(rows)
                            .append(",minmax(0,1fr));");
                }
            }
            case Components.LAZY_HGRID -> {
                if (tracksCss != null) {
                    css.append("grid-template-rows:").append(tracksCss).append(';');
                } else {
                    Integer rows = RenderNode.gridCount(node, Properties.GRID_ROWS);
                    if (rows != null) {
                        css.append("grid-template-rows:repeat(").append(rows)
                                .append(",minmax(0,1fr));");
                    }
                }
                css.append("grid-auto-flow:column;grid-auto-columns:minmax(0,1fr);");
            }
            default -> {
                if (tracksCss != null) {
                    css.append("grid-template-columns:").append(tracksCss).append(';');
                } else if (columns != null) {
                    css.append("grid-template-columns:repeat(").append(columns)
                            .append(",minmax(0,1fr));");
                }
            }
        }
        return css.toString();
    }

    private static List<String> gridTracks(RenderNode node) {
        String spec = node.strings.get(Properties.GRID_TRACKS);
        if (spec == null) {
            return null;
        }
        List<String> tracks = new ArrayList<>();
        for (String track : spec.split(",")) {
            tracks.add(track.trim());
        }
        return tracks;
    }

    private static String gridTracksCss(RenderNode node) {
        List<String> tracks = gridTracks(node);
        if (tracks == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String track : tracks) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            if (track.startsWith("fixed:")) {
                out.append(track.substring("fixed:".length())).append("px");
            } else if (track.startsWith("adaptive:")) {
                out.append("minmax(").append(track.substring("adaptive:".length()))
                        .append("px,1fr)");
            } else {
                out.append("1fr");
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Attributes / text
    // ------------------------------------------------------------------

    private static String slotAttrs(RenderNode node) {
        StringBuilder attrs = new StringBuilder();
        String route = node.strings.get(Properties.ROUTE);
        if (route != null) {
            attrs.append(" data-pathland-route=\"").append(Format.escape(route)).append('"');
        }
        int chrome = (int) Math.round(node.f32Property(Properties.NAV_CHROME, 0.0f));
        if (chrome == 1) {
            attrs.append(" data-pathland-nav-chrome=\"custom\"");
        }
        int depth = Math.max(node.u32Property(Properties.NAV_DEPTH, 1), 1);
        if (depth > 1) {
            attrs.append(" data-pathland-depth=\"").append(Integer.toUnsignedString(depth))
                    .append('"');
        }
        int transition = (int) Math.round(node.f32Property(Properties.TRANSITION, 0.0f));
        if (transition != 0) {
            String name = switch (transition) {
                case 2 -> "fade";
                case 3 -> "slide";
                case 4 -> "scale";
                default -> "platform";
            };
            attrs.append(" data-pathland-transition=\"").append(name).append('"');
        }
        return attrs.toString();
    }

    private static String eventAttrs(RenderNode node) {
        StringBuilder attrs = new StringBuilder();
        Integer listeners = node.properties.get(Properties.EVENT_LISTENERS);
        if (listeners != null && listeners != 0) {
            attrs.append(" data-event-listeners=\"").append(Integer.toUnsignedString(listeners))
                    .append('"');
        }
        Integer action = node.properties.get(Properties.ACTION_ID);
        if (action != null) {
            attrs.append(" data-action-id=\"").append(Integer.toUnsignedString(action)).append('"');
        }
        Integer binding = node.properties.get(Properties.BINDING_ID);
        if (binding != null) {
            attrs.append(" data-binding-id=\"").append(Integer.toUnsignedString(binding)).append('"');
        }
        return attrs.toString();
    }

    private static String ariaAttrs(RenderNode node) {
        StringBuilder attrs = new StringBuilder();
        Integer roleBits = node.properties.get(Properties.ROLE);
        if (roleBits != null) {
            int code = ((int) Float.intBitsToFloat(roleBits)) & 0xFF;
            String role = RoleSpec.ariaRole(RoleSpec.shellKind(node.component), code);
            if (role != null) {
                attrs.append(" role=\"").append(role).append('"');
            }
        }
        Integer stateBits = node.properties.get(Properties.STATE);
        if (stateBits != null) {
            switch (((int) Float.intBitsToFloat(stateBits)) & 0xFF) {
                case 1 -> attrs.append(" aria-disabled=\"true\"");
                case 3 -> attrs.append(" aria-pressed=\"true\"");
                case 4 -> attrs.append(" aria-selected=\"true\"");
                case 5 -> attrs.append(" aria-expanded=\"true\"");
                case 6 -> attrs.append(" aria-busy=\"true\"");
                default -> {}
            }
        }
        Integer enabled = node.properties.get(Properties.ENABLED);
        if (enabled != null && enabled == 0) {
            attrs.append(" disabled");
        }
        if (node.component != Components.ICON
                && node.component != Components.IMAGE
                && node.component != Components.SLIDER
                && node.component != Components.TEXT_FIELD) {
            String label = node.strings.get(Properties.LABEL);
            if (label != null && !label.isEmpty()) {
                attrs.append(" aria-label=\"").append(Format.escapeHtmlAttr(label)).append('"');
            }
        }
        return attrs.toString();
    }

    private static String renderAttributed(AttributedString value) {
        String text = value.getText();
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        TreeSet<Integer> boundarySet = new TreeSet<>();
        boundarySet.add(0);
        boundarySet.add(textBytes.length);
        for (StyleRun run : value.getRuns()) {
            boundarySet.add(run.getStart());
            boundarySet.add(run.getEnd());
        }
        for (LinkRun link : value.getLinks()) {
            boundarySet.add(link.getStart());
            boundarySet.add(link.getEnd());
        }
        List<Integer> boundaries = new ArrayList<>(boundarySet);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i + 1 < boundaries.size(); i++) {
            int start = boundaries.get(i);
            int end = boundaries.get(i + 1);
            if (start == end) {
                continue;
            }
            List<TextAttribute> attributes = new ArrayList<>();
            for (StyleRun run : value.getRuns()) {
                if (run.getStart() <= start && run.getEnd() >= end) {
                    for (TextAttribute attribute : run.getAttributes()) {
                        int existing = -1;
                        for (int j = 0; j < attributes.size(); j++) {
                            if (attributes.get(j).getProperty() == attribute.getProperty()) {
                                existing = j;
                                break;
                            }
                        }
                        if (existing >= 0) {
                            attributes.set(existing, attribute);
                        } else {
                            attributes.add(attribute);
                        }
                    }
                }
            }
            String link = null;
            for (LinkRun candidate : value.getLinks()) {
                if (candidate.getStart() <= start && candidate.getEnd() >= end) {
                    link = candidate.getUrl();
                }
            }
            String body = Format.escape(new String(textBytes, start, end - start,
                    StandardCharsets.UTF_8));
            String css = runStyleCss(attributes);
            String rendered;
            if (link != null) {
                String style = css.isEmpty() ? "" : " style=\"" + Format.escapeHtmlAttr(css) + "\"";
                rendered = "<a href=\"" + Format.escapeHtmlAttr(link) + "\"" + style + ">" + body
                        + "</a>";
            } else if (css.isEmpty()) {
                rendered = body;
            } else {
                rendered = "<span style=\"" + Format.escapeHtmlAttr(css) + "\">" + body + "</span>";
            }
            out.append(rendered);
        }
        return out.toString();
    }

    private static String runStyleCss(List<TextAttribute> attributes) {
        StringBuilder css = new StringBuilder();
        boolean underline = false;
        boolean strikethrough = false;
        for (TextAttribute attribute : attributes) {
            switch (attribute.getProperty()) {
                case Properties.FONT_SIZE -> {
                    float value = Float.intBitsToFloat(attribute.getValue());
                    if (value > 0.0f) {
                        css.append("font-size:").append(Format.f32(value)).append("px;");
                    }
                }
                case Properties.FONT_WEIGHT -> {
                    float value = Float.intBitsToFloat(attribute.getValue());
                    if (value > 0.0f) {
                        css.append("font-weight:").append(Format.f32(value)).append(';');
                    }
                }
                case Properties.COLOR -> css.append("color:").append(Format.rgba(attribute.getValue()))
                        .append(';');
                case Properties.FONT_STYLE -> {
                    if (((int) Float.intBitsToFloat(attribute.getValue())) == 1) {
                        css.append("font-style:italic;");
                    }
                }
                case Properties.FONT_DESIGN -> {
                    String family = switch ((int) Float.intBitsToFloat(attribute.getValue())) {
                        case 1 -> "Georgia, 'Times New Roman', serif";
                        case 2 -> "ui-rounded, system-ui, sans-serif";
                        case 3 -> "ui-monospace, monospace";
                        default -> "inherit";
                    };
                    css.append("font-family:").append(family).append(';');
                }
                case Properties.UNDERLINE -> underline |= attribute.getValue() != 0;
                case Properties.STRIKETHROUGH -> strikethrough |= attribute.getValue() != 0;
                default -> {}
            }
        }
        if (underline || strikethrough) {
            List<String> decoration = new ArrayList<>();
            if (underline) {
                decoration.add("underline");
            }
            if (strikethrough) {
                decoration.add("line-through");
            }
            css.append("text-decoration:").append(String.join(" ", decoration)).append(';');
        }
        return css.toString();
    }

    // ------------------------------------------------------------------
    // Dates
    // ------------------------------------------------------------------

    private static int[] daysToDate(int days) {
        long z = (long) days + 719468L;
        long era = (z >= 0 ? z : z - 146096L) / 146097L;
        long doe = z - era * 146097L;
        long yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L;
        long y = yoe + era * 400L;
        long doy = doe - (365L * yoe + yoe / 4L - yoe / 100L);
        long mp = (5L * doy + 2L) / 153L;
        long d = doy - (153L * mp + 2L) / 5L + 1L;
        long m = mp < 10L ? mp + 3L : mp - 9L;
        long year = m <= 2L ? y + 1L : y;
        return new int[] {(int) year, (int) m, (int) d};
    }

    private static int[] millisToHms(int millis) {
        int total = millis / 1000;
        return new int[] {total / 3600, (total % 3600) / 60, total % 60};
    }
}
