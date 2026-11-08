package dev.pathland.render.html;

import dev.pathland.core.text.AttributedString;
import dev.pathland.core.protocol.Components;
import dev.pathland.core.protocol.Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** A decoded node in the renderer's transient output tree. */
final class RenderNode {

    static final float FILL = -1.0f;
    static final float HUG_CONTENT = -2.0f;

    record DateValue(int days, int millis) {}

    int component;
    String text;
    AttributedString attributed;
    final TreeMap<Integer, Integer> properties = new TreeMap<>();
    final TreeMap<Integer, String> strings = new TreeMap<>();
    final TreeMap<Integer, String> tokenRefs = new TreeMap<>();
    DateValue date;
    final List<Integer> children = new ArrayList<>();
    final List<Float> fitThresholds = new ArrayList<>();

    RenderNode(int component) {
        this.component = component;
    }

    boolean checked() {
        Integer value = properties.get(Properties.SELECTED);
        return (value == null ? 0 : value) != 0;
    }

    int roleCode() {
        Integer bits = properties.get(Properties.ROLE);
        return bits == null ? 0 : ((int) Float.intBitsToFloat(bits)) & 0xFF;
    }

    Integer textStyleCode() {
        Integer bits = properties.get(Properties.TEXT_STYLE);
        return bits == null ? null : (((int) Float.intBitsToFloat(bits)) & 0xFF);
    }

    float f32Property(int prop, float defaultValue) {
        Integer bits = properties.get(prop);
        return bits == null ? defaultValue : Float.intBitsToFloat(bits);
    }

    Float f32(int prop) {
        Integer bits = properties.get(prop);
        return bits == null ? null : Float.intBitsToFloat(bits);
    }

    Float contentMargins() {
        Integer bits = properties.get(Properties.CONTENT_MARGINS);
        return bits == null ? null : Float.intBitsToFloat(bits);
    }

    int u32Property(int prop, int defaultValue) {
        Integer value = properties.get(prop);
        return value == null ? defaultValue : value;
    }

    String tokenRef(int prop) {
        String path = tokenRefs.get(prop);
        return path == null ? null : Tokens.resolveTokenRef(path);
    }

    String styleCss() {
        StringBuilder css = new StringBuilder();

        String tokenColor = tokenRef(Properties.COLOR);
        if (tokenColor != null) {
            css.append("color:").append(tokenColor).append(';');
        } else {
            Integer bits = properties.get(Properties.COLOR);
            if (bits != null) {
                css.append("color:").append(Format.rgba(bits)).append(';');
            }
        }
        String tokenBackground = tokenRef(Properties.BACKGROUND_COLOR);
        if (tokenBackground != null) {
            css.append("background-color:").append(tokenBackground).append(';');
        } else {
            Integer bits = properties.get(Properties.BACKGROUND_COLOR);
            if (bits != null) {
                css.append("background-color:").append(Format.rgba(bits)).append(';');
            }
        }
        Float design = f32(Properties.FONT_DESIGN);
        if (design != null) {
            String family = fontDesignFamily((int) (design.floatValue()) & 0xFF);
            if (family != null) {
                css.append("font-family:").append(family).append(';');
            }
        }
        String tokenFamily = tokenRef(Properties.FONT_FAMILY);
        if (tokenFamily != null) {
            css.append("font-family:").append(tokenFamily).append(';');
        } else {
            String family = strings.get(Properties.FONT_FAMILY);
            if (family != null) {
                css.append("font-family:'").append(family.replace("'", "\\'")).append("';");
            }
        }

        // Layout / distance (dp → px).
        String tokenSpacing = tokenRef(Properties.SPACING);
        if (tokenSpacing != null) {
            css.append("gap:").append(tokenSpacing).append(';');
        } else {
            Float v = f32(Properties.SPACING);
            if (v != null && v > 0.0f) {
                css.append("gap:").append(Format.f32(v)).append("px;");
            }
        }
        String tokenMargins = tokenRef(Properties.CONTENT_MARGINS);
        if (tokenMargins != null) {
            css.append("padding:").append(tokenMargins).append(';');
        } else {
            Float m = contentMargins();
            if (m != null && m > 0.0f) {
                css.append("padding:").append(Format.f32(m)).append("px;");
            }
        }
        String tokenPadding = tokenRef(Properties.PADDING);
        if (tokenPadding != null) {
            css.append("padding:").append(tokenPadding).append(';');
        } else {
            Float v = f32(Properties.PADDING);
            if (v != null && v > 0.0f) {
                css.append("padding:").append(Format.f32(v)).append("px;");
            }
        }
        int[][] sides = {
                {Properties.PADDING_TOP, 0},
                {Properties.PADDING_RIGHT, 0},
                {Properties.PADDING_BOTTOM, 0},
                {Properties.PADDING_LEFT, 0},
        };
        String[] sideNames = {"padding-top", "padding-right", "padding-bottom", "padding-left"};
        for (int i = 0; i < sides.length; i++) {
            int prop = sides[i][0];
            String token = tokenRef(prop);
            if (token != null) {
                css.append(sideNames[i]).append(':').append(token).append(';');
            } else {
                Float v = f32(prop);
                if (v != null && v > 0.0f) {
                    css.append(sideNames[i]).append(':').append(Format.f32(v)).append("px;");
                }
            }
        }
        String tokenWidth = tokenRef(Properties.WIDTH);
        if (tokenWidth != null) {
            css.append("width:").append(tokenWidth).append(';');
        } else {
            Float v = f32(Properties.WIDTH);
            if (v != null && v != HUG_CONTENT) {
                css.append("width:").append(sizeCss(v)).append(';');
            }
        }
        String tokenHeight = tokenRef(Properties.HEIGHT);
        if (tokenHeight != null) {
            css.append("height:").append(tokenHeight).append(';');
        } else {
            Float v = f32(Properties.HEIGHT);
            if (v != null && v != HUG_CONTENT) {
                css.append("height:").append(sizeCss(v)).append(';');
            }
        }
        int[][] frames = {
                {Properties.MIN_WIDTH, 0},
                {Properties.MAX_WIDTH, 0},
                {Properties.MIN_HEIGHT, 0},
                {Properties.MAX_HEIGHT, 0},
        };
        String[] frameNames = {
                "min-width", "max-width", "min-height", "max-height",
        };
        for (int i = 0; i < frames.length; i++) {
            Float v = f32(frames[i][0]);
            if (v != null) {
                css.append(frameNames[i]).append(':').append(Format.f32(v)).append("px;");
            }
        }
        Float offsetX = f32(Properties.OFFSET_X);
        Float offsetY = f32(Properties.OFFSET_Y);
        if (offsetX != null && offsetY != null) {
            if (offsetX != 0.0f || offsetY != 0.0f) {
                css.append("transform:translate(").append(Format.f32(offsetX)).append("px, ")
                        .append(Format.f32(offsetY)).append("px);");
            }
        } else {
            Float x = f32(Properties.POSITION_X);
            if (x != null && x != 0.0f) {
                css.append("left:").append(Format.f32(x)).append("px;");
            }
            Float y = f32(Properties.POSITION_Y);
            if (y != null && y != 0.0f) {
                css.append("top:").append(Format.f32(y)).append("px;");
            }
        }

        // Typography.
        Integer style = textStyleCode();
        if (style != null) {
            float[] scale = textStyleScale(style);
            if (scale != null) {
                css.append("font-size:").append(Format.f32(scale[0])).append("px;font-weight:")
                        .append(Format.f32(scale[1])).append(';');
            }
        }
        String tokenFontSize = tokenRef(Properties.FONT_SIZE);
        if (tokenFontSize != null) {
            css.append("font-size:").append(tokenFontSize).append(';');
        } else {
            Float v = f32(Properties.FONT_SIZE);
            if (v != null && v > 0.0f) {
                css.append("font-size:").append(Format.f32(v)).append("px;");
            }
        }
        String tokenFontWeight = tokenRef(Properties.FONT_WEIGHT);
        if (tokenFontWeight != null) {
            css.append("font-weight:").append(tokenFontWeight).append(';');
        } else {
            Float v = f32(Properties.FONT_WEIGHT);
            if (v != null && v > 0.0f) {
                css.append("font-weight:").append(Format.f32(v)).append(';');
            }
        }
        String tokenRadius = tokenRef(Properties.BORDER_RADIUS);
        if (tokenRadius != null) {
            css.append("border-radius:").append(tokenRadius).append(';');
        } else {
            Float v = f32(Properties.BORDER_RADIUS);
            if (v != null && v != 0.0f) {
                css.append("border-radius:").append(Format.f32(v)).append("px;");
            }
        }

        // Effects.
        List<String> filters = new ArrayList<>();
        Float blur = f32(Properties.BLUR_RADIUS);
        if (blur != null) {
            filters.add("blur(" + Format.f32(blur) + "px)");
        }
        Float saturation = f32(Properties.SATURATION);
        if (saturation != null) {
            filters.add("saturate(" + Format.f32(saturation) + ")");
        }
        Float contrast = f32(Properties.CONTRAST);
        if (contrast != null) {
            filters.add("contrast(" + Format.f32(contrast) + ")");
        }
        Float brightness = f32(Properties.BRIGHTNESS);
        if (brightness != null) {
            filters.add("brightness(" + Format.f32(brightness) + ")");
        }
        Float grayscale = f32(Properties.GRAYSCALE);
        if (grayscale != null) {
            filters.add("grayscale(" + Format.f32(grayscale) + ")");
        }
        Float hue = f32(Properties.HUE_ROTATION);
        if (hue != null) {
            filters.add("hue-rotate(" + Format.f32(hue) + "deg)");
        }
        if (!filters.isEmpty()) {
            css.append("filter:").append(String.join(" ", filters)).append(';');
        }
        Float rotation = f32(Properties.ROTATION_DEGREES);
        if (rotation != null && rotation != 0.0f) {
            css.append("transform:rotate(").append(Format.f32(rotation)).append("deg);");
        }
        Float scale = f32(Properties.SCALE);
        if (scale != null && scale != 1.0f) {
            css.append("transform:scale(").append(Format.f32(scale)).append(");");
        }
        String shadowRadiusRef = tokenRef(Properties.SHADOW_RADIUS);
        String shadowXRef = tokenRef(Properties.SHADOW_X);
        String shadowYRef = tokenRef(Properties.SHADOW_Y);
        float shadowRadius = f32Property(Properties.SHADOW_RADIUS, 0.0f);
        float shadowX = f32Property(Properties.SHADOW_X, 0.0f);
        float shadowY = f32Property(Properties.SHADOW_Y, 0.0f);
        if (shadowRadiusRef != null || shadowXRef != null || shadowYRef != null
                || shadowRadius != 0.0f || shadowX != 0.0f || shadowY != 0.0f) {
            String color = tokenRef(Properties.SHADOW_COLOR);
            if (color == null) {
                Integer bits = properties.get(Properties.SHADOW_COLOR);
                color = bits == null ? "rgba(0,0,0,0.3)" : Format.rgba(bits);
            }
            String x = shadowXRef != null ? shadowXRef : Format.f32(shadowX) + "px";
            String y = shadowYRef != null ? shadowYRef : Format.f32(shadowY) + "px";
            String r = shadowRadiusRef != null ? shadowRadiusRef : Format.f32(shadowRadius) + "px";
            css.append("box-shadow:").append(color).append(' ').append(x).append(' ').append(y)
                    .append(' ').append(r).append(';');
        }
        String tokenOpacity = tokenRef(Properties.OPACITY);
        if (tokenOpacity != null) {
            css.append("opacity:").append(tokenOpacity).append(';');
        } else {
            Float v = f32(Properties.OPACITY);
            if (v != null && v < 1.0f) {
                css.append("opacity:").append(Format.f32(v)).append(';');
            }
        }
        Float zIndex = f32(Properties.Z_INDEX);
        if (zIndex != null) {
            css.append("z-index:").append((int) zIndex.floatValue()).append(';');
        }

        Float alignment = f32(Properties.ALIGNMENT);
        if (alignment != null) {
            if (!(component == Components.ZSTACK
                    || component == Components.GRID
                    || component == Components.LAZY_VGRID
                    || component == Components.LAZY_HGRID)) {
                css.append("align-items:")
                        .append(switch ((int) alignment.floatValue() & 0xFF) {
                            case 0 -> "flex-start";
                            case 1 -> "center";
                            case 2 -> "flex-end";
                            default -> "flex-start";
                        })
                        .append(';');
            }
        }
        Float textAlignment = f32(Properties.TEXT_ALIGNMENT);
        if (textAlignment != null) {
            int code = Math.min((int) textAlignment.floatValue() & 0xFF, 2);
            css.append("text-align:")
                    .append(switch (code) {
                        case 0 -> "left";
                        case 1 -> "center";
                        default -> "right";
                    })
                    .append(';');
        }
        Float textCase = f32(Properties.TEXT_CASE);
        if (textCase != null) {
            css.append("text-transform:")
                    .append(switch ((int) textCase.floatValue() & 0xFF) {
                        case 1 -> "uppercase";
                        case 2 -> "lowercase";
                        default -> "none";
                    })
                    .append(';');
        }
        Integer visible = properties.get(Properties.VISIBLE);
        if (visible != null && visible == 0) {
            css.append("display:none;");
        }
        Float fontStyle = f32(Properties.FONT_STYLE);
        if (fontStyle != null && fontStyle != 0.0f) {
            css.append("font-style:italic;");
        }
        Float fontDesign = f32(Properties.FONT_DESIGN);
        if (fontDesign != null) {
            css.append("font-family:")
                    .append(switch ((int) fontDesign.floatValue() & 0xFF) {
                        case 1 -> "Georgia, 'Times New Roman', serif";
                        case 2 -> "ui-rounded, system-ui, sans-serif";
                        case 3 -> "ui-monospace, monospace";
                        default -> "inherit";
                    })
                    .append(';');
        }
        List<String> decoration = new ArrayList<>();
        Float underline = f32(Properties.UNDERLINE);
        if (underline != null && underline != 0.0f) {
            decoration.add("underline");
        }
        Float strikethrough = f32(Properties.STRIKETHROUGH);
        if (strikethrough != null && strikethrough != 0.0f) {
            decoration.add("line-through");
        }
        if (!decoration.isEmpty()) {
            css.append("text-decoration:").append(String.join(" ", decoration)).append(';');
        }
        Float clips = f32(Properties.CLIPS_TO_BOUNDS);
        if (clips != null && clips != 0.0f) {
            css.append("overflow:hidden;");
        }
        Float allowsHitTesting = f32(Properties.ALLOWS_HIT_TESTING);
        if (allowsHitTesting != null && allowsHitTesting == 0.0f) {
            css.append("pointer-events:none;");
        }
        Float colorInvert = f32(Properties.COLOR_INVERT);
        if (colorInvert != null && colorInvert != 0.0f) {
            css.append("filter:invert(1);");
        }
        Float contentMode = f32(Properties.CONTENT_MODE);
        if (contentMode != null) {
            css.append("object-fit:")
                    .append(((int) contentMode.floatValue() & 0xFF) == 1 ? "cover" : "contain")
                    .append(';');
        }
        Float aspectRatio = f32(Properties.ASPECT_RATIO);
        if (aspectRatio != null) {
            css.append("aspect-ratio:").append(Format.f32(aspectRatio)).append(';');
        }
        return css.toString();
    }

    String borderStyle() {
        String width;
        String tokenWidth = tokenRef(Properties.BORDER_WIDTH);
        if (tokenWidth != null) {
            width = tokenWidth;
        } else {
            Integer bits = properties.get(Properties.BORDER_WIDTH);
            if (bits == null) {
                return "";
            }
            width = Format.f32(Float.intBitsToFloat(bits)) + "px";
        }
        String color;
        String tokenColor = tokenRef(Properties.BORDER_COLOR);
        if (tokenColor != null) {
            color = tokenColor;
        } else {
            Integer bits = properties.get(Properties.BORDER_COLOR);
            color = Format.rgba(bits == null ? 0xFF000000 : bits);
        }
        int edges = properties.getOrDefault(Properties.BORDER_EDGES, 0xF);

        StringBuilder css = new StringBuilder();
        int[] flags = {1, 2, 4, 8};
        String[] sides = {"top", "left", "bottom", "right"};
        for (int i = 0; i < flags.length; i++) {
            if ((edges & flags[i]) != 0) {
                css.append("border-").append(sides[i]).append(':').append(width)
                        .append(" solid ").append(color).append(';');
            }
        }
        return css.toString();
    }

    private static float[] textStyleScale(int code) {
        return switch (code) {
            case 0 -> new float[] {34.0f, 700.0f};
            case 1 -> new float[] {28.0f, 700.0f};
            case 2 -> new float[] {22.0f, 700.0f};
            case 3 -> new float[] {20.0f, 600.0f};
            case 4 -> new float[] {17.0f, 600.0f};
            case 5 -> new float[] {15.0f, 400.0f};
            case 6 -> new float[] {15.0f, 400.0f};
            case 7 -> new float[] {14.0f, 400.0f};
            case 8 -> new float[] {13.0f, 400.0f};
            case 9 -> new float[] {12.0f, 400.0f};
            case 10 -> new float[] {11.0f, 400.0f};
            default -> null;
        };
    }

    private static String fontDesignFamily(int code) {
        return switch (code) {
            case 1 -> "serif";
            case 2 -> "sans-serif";
            case 3 -> "ui-monospace, SFMono-Regular, Menlo, monospace";
            default -> null;
        };
    }

    static String sizeCss(float value) {
        if (value == FILL) {
            return "100%";
        }
        if (value == HUG_CONTENT) {
            return "fit-content";
        }
        return Format.f32(value) + "px";
    }

    /** Whether a fit slot satisfies the candidate-identity contract. */
    boolean fitCandidatesValid() {
        if (children.size() <= 1 || fitThresholds.size() != children.size()) {
            return false;
        }
        for (int i = 1; i < fitThresholds.size(); i++) {
            if (fitThresholds.get(i - 1) > fitThresholds.get(i)) {
                return false;
            }
        }
        return true;
    }

    static Boolean stackMainHorizontal(int component) {
        return switch (component) {
            case Components.HSTACK, Components.LAZY_HSTACK -> Boolean.TRUE;
            case Components.VSTACK, Components.LAZY_VSTACK -> Boolean.FALSE;
            default -> null;
        };
    }

    static Float axisMax(RenderNode node, boolean axisHorizontal) {
        int prop = axisHorizontal ? Properties.MAX_WIDTH : Properties.MAX_HEIGHT;
        Float value = node.f32(prop);
        return value != null && Float.isFinite(value) && value >= 0.0f ? value : null;
    }

    static boolean scrollHorizontal(RenderNode node) {
        return Math.round(node.f32Property(Properties.SCROLL_AXIS, 0.0f)) != 0;
    }

    static boolean fillsAxis(TreeMap<Integer, RenderNode> nodes, int id,
            boolean axisHorizontal, boolean mainAxis) {
        RenderNode node = nodes.get(id);
        if (node == null) {
            return false;
        }
        // A finite max is a cap, not a fill request: it keeps the node
        // content-sized up to the cap, lets the parent ALIGNMENT position it,
        // and stops unbounded fill propagation to ancestors.
        if (axisMax(node, axisHorizontal) != null) {
            return false;
        }
        int prop = axisHorizontal ? Properties.WIDTH : Properties.HEIGHT;
        Float hint = node.f32(prop);
        if (hint != null) {
            if (hint == FILL) {
                return true;
            }
            if (hint == HUG_CONTENT) {
                return false; // explicit hug overrides greed
            }
            if (Float.isFinite(hint) && hint > 0.0f) {
                return false;
            }
        }
        if (node.component == Components.SCROLLVIEW) {
            boolean scrollHorizontal = scrollHorizontal(node);
            if (scrollHorizontal == axisHorizontal) {
                return true; // greedy on its scroll axis
            }
            if (node.children.isEmpty()) {
                return false;
            }
            return fillsAxis(nodes, node.children.get(0), axisHorizontal, false);
        }
        boolean greedy = switch (node.component) {
            case Components.COLOR -> true;
            case Components.SPACER -> mainAxis;
            case Components.DIVIDER -> !mainAxis;
            default -> false;
        };
        if (greedy) {
            return true;
        }
        if (!(node.component == Components.VSTACK
                || node.component == Components.HSTACK
                || node.component == Components.ZSTACK
                || node.component == Components.LAZY_VSTACK
                || node.component == Components.LAZY_HSTACK)) {
            return false;
        }
        Boolean stackHorizontal = stackMainHorizontal(node.component);
        boolean childMain = stackHorizontal != null && stackHorizontal == axisHorizontal;
        for (int child : node.children) {
            if (fillsAxis(nodes, child, axisHorizontal, childMain)) {
                return true;
            }
        }
        return false;
    }

    static String fillPropagation(TreeMap<Integer, RenderNode> nodes, int id, RenderNode node,
            boolean mainHorizontal) {
        StringBuilder extra = new StringBuilder();
        boolean[] axes = {true, false};
        int[] props = {Properties.WIDTH, Properties.HEIGHT};
        String[] css = {"width:100%;", "height:100%;"};
        for (int i = 0; i < axes.length; i++) {
            Float hint = node.f32(props[i]);
            boolean hug = hint == null || hint == HUG_CONTENT;
            if (hug && fillsAxis(nodes, id, axes[i], mainHorizontal == axes[i])) {
                extra.append(css[i]);
            }
        }
        return extra.toString();
    }

    static Integer gridCount(RenderNode node, int prop) {
        float value = node.f32Property(prop, -1.0f);
        return value <= 0.0f ? null : (int) Math.round(value);
    }

    static String alignH(int code) {
        return switch (code) {
            case 1, 3, 4 -> "center";
            case 2, 6, 7 -> "end";
            default -> "start";
        };
    }

    static String alignV(int code) {
        return switch (code) {
            case 1, 5, 6 -> "center";
            case 2, 4, 8 -> "end";
            default -> "start";
        };
    }
}
