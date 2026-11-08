package dev.pathland.render.html;

import dev.pathland.core.protocol.Components;
import dev.pathland.core.protocol.Properties;
import dev.pathland.core.protocol.ValueTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Debug-mode HTML comments describing each rendered node, ported from the Rust {@code debug} module. */
final class RenderDebug {

    private RenderDebug() {}

    static String componentName(int component) {
        return switch (component) {
            case Components.TEXT -> "Text";
            case Components.IMAGE -> "Image";
            case Components.COLOR -> "Color";
            case Components.SHAPE -> "Shape";
            case Components.DIVIDER -> "Divider";
            case Components.SPACER -> "Spacer";
            case Components.PROGRESS_VIEW -> "ProgressView";
            case Components.GAUGE -> "Gauge";
            case Components.VSTACK -> "VStack";
            case Components.HSTACK -> "HStack";
            case Components.ZSTACK -> "ZStack";
            case Components.GRID -> "Grid";
            case Components.SCROLLVIEW -> "ScrollView";
            case Components.LAZY_VGRID -> "LazyVGrid";
            case Components.LAZY_HGRID -> "LazyHGrid";
            case Components.LAZY_VSTACK -> "LazyVStack";
            case Components.LAZY_HSTACK -> "LazyHStack";
            case Components.BUTTON -> "Button";
            case Components.TEXT_FIELD -> "TextField";
            case Components.TEXT_EDITOR -> "TextEditor";
            case Components.TOGGLE -> "Toggle";
            case Components.SLIDER -> "Slider";
            case Components.STEPPER -> "Stepper";
            case Components.DATE_PICKER -> "DatePicker";
            case Components.PICKER -> "Picker";
            case Components.MENU -> "Menu";
            case Components.COLOR_PICKER -> "ColorPicker";
            case Components.COMMENT -> "Comment";
            default -> "Unknown";
        };
    }

    static String propertyName(int prop) {
        return switch (prop) {
            case Properties.SPACING -> "spacing";
            case Properties.ALIGNMENT -> "alignment";
            case Properties.CONTENT_MARGINS -> "contentMargins";
            case Properties.TEXT -> "text";
            case Properties.LINE_LIMIT -> "lineLimit";
            case Properties.TEXT_ALIGNMENT -> "textAlignment";
            case Properties.TRUNCATION_MODE -> "truncationMode";
            case Properties.SHAPE_KIND -> "shapeKind";
            case Properties.OFFSET_X -> "offsetX";
            case Properties.OFFSET_Y -> "offsetY";
            case Properties.POSITION_X -> "positionX";
            case Properties.POSITION_Y -> "positionY";
            case Properties.MIN_WIDTH -> "minWidth";
            case Properties.MAX_WIDTH -> "maxWidth";
            case Properties.MIN_HEIGHT -> "minHeight";
            case Properties.MAX_HEIGHT -> "maxHeight";
            case Properties.FIXED_SIZE_HORIZONTAL -> "fixedSizeHorizontal";
            case Properties.FIXED_SIZE_VERTICAL -> "fixedSizeVertical";
            case Properties.LAYOUT_PRIORITY -> "layoutPriority";
            case Properties.ASPECT_RATIO -> "aspectRatio";
            case Properties.CONTENT_MODE -> "contentMode";
            case Properties.MINIMUM_SCALE_FACTOR -> "minimumScaleFactor";
            case Properties.BACKGROUND_COLOR -> "backgroundColor";
            case Properties.IMAGE_SOURCE -> "imageSource";
            case Properties.ICON_NAME -> "iconName";
            case Properties.BORDER_WIDTH -> "borderWidth";
            case Properties.BORDER_COLOR -> "borderColor";
            case Properties.BORDER_RADIUS -> "borderRadius";
            case Properties.PADDING_STYLE -> "paddingStyle";
            case Properties.FONT_SIZE -> "fontSize";
            case Properties.FONT_WEIGHT -> "fontWeight";
            case Properties.FONT_FAMILY -> "fontFamily";
            case Properties.COLOR -> "color";
            case Properties.WIDTH -> "width";
            case Properties.HEIGHT -> "height";
            case Properties.OPACITY -> "opacity";
            case Properties.VISIBLE -> "visible";
            case Properties.Z_INDEX -> "zIndex";
            case Properties.CLIPS_TO_BOUNDS -> "clipsToBounds";
            case Properties.PADDING -> "padding";
            case Properties.PADDING_TOP -> "paddingTop";
            case Properties.PADDING_RIGHT -> "paddingRight";
            case Properties.PADDING_BOTTOM -> "paddingBottom";
            case Properties.PADDING_LEFT -> "paddingLeft";
            case Properties.BORDER_EDGES -> "borderEdges";
            case Properties.FONT_STYLE -> "fontStyle";
            case Properties.FONT_DESIGN -> "fontDesign";
            case Properties.FONT_WIDTH -> "fontWidth";
            case Properties.KERNING -> "kerning";
            case Properties.TRACKING -> "tracking";
            case Properties.BASELINE_OFFSET -> "baselineOffset";
            case Properties.LINE_SPACING -> "lineSpacing";
            case Properties.TEXT_CASE -> "textCase";
            case Properties.UNDERLINE -> "underline";
            case Properties.STRIKETHROUGH -> "strikethrough";
            case Properties.SHADOW_COLOR -> "shadowColor";
            case Properties.SHADOW_RADIUS -> "shadowRadius";
            case Properties.SHADOW_X -> "shadowX";
            case Properties.SHADOW_Y -> "shadowY";
            case Properties.BLUR_RADIUS -> "blurRadius";
            case Properties.SATURATION -> "saturation";
            case Properties.CONTRAST -> "contrast";
            case Properties.BRIGHTNESS -> "brightness";
            case Properties.GRAYSCALE -> "grayscale";
            case Properties.HUE_ROTATION -> "hueRotation";
            case Properties.COLOR_MULTIPLY -> "colorMultiply";
            case Properties.COLOR_INVERT -> "colorInvert";
            case Properties.ROTATION_DEGREES -> "rotation";
            case Properties.SCALE -> "scale";
            case Properties.ALLOWS_HIT_TESTING -> "allowsHitTesting";
            case Properties.TINT -> "tint";
            case Properties.TRANSITION -> "transition";
            case Properties.ROLE -> "role";
            case Properties.STATE -> "state";
            case Properties.ENABLED -> "enabled";
            case Properties.SELECTED -> "selected";
            case Properties.EVENT_LISTENERS -> "eventListeners";
            case Properties.VALUE -> "value";
            case Properties.MIN_VALUE -> "minValue";
            case Properties.MAX_VALUE -> "maxValue";
            case Properties.LABEL -> "label";
            case Properties.PROMPT -> "prompt";
            case Properties.STEP_VALUE -> "step";
            case Properties.CONTROL_SIZE -> "controlSize";
            case Properties.IS_SECURE -> "isSecure";
            case Properties.PROGRESS -> "progress";
            case Properties.IS_INDETERMINATE -> "isIndeterminate";
            case Properties.SELECTION -> "selection";
            case Properties.COLOR_VALUE -> "colorValue";
            case Properties.DATE_PICKER_MODE -> "datePickerMode";
            case Properties.PICKER_STYLE -> "pickerStyle";
            case Properties.ACTION_ID -> "actionId";
            case Properties.BINDING_ID -> "bindingId";
            case Properties.TOGGLE_STYLE -> "toggleStyle";
            case Properties.ROUTE -> "route";
            case Properties.NAV_DEPTH -> "navDepth";
            case Properties.NAV_CHROME -> "navChrome";
            default -> "unknown";
        };
    }

    static String nodeComment(int id, RenderNode node) {
        StringBuilder content = new StringBuilder(" #").append(id).append(' ')
                .append(componentName(node.component));
        if (node.text != null && !node.text.isEmpty()) {
            content.append(' ').append(quote(node.text));
        }
        if (!node.properties.isEmpty()) {
            List<String> props = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : node.properties.entrySet()) {
                props.add(propertyName(entry.getKey()) + "=" + formatValue(node, entry.getKey()));
            }
            content.append(": ").append(String.join(", ", props));
        }
        return "<!--" + scrub(content.toString()) + " -->";
    }

    private static String formatValue(RenderNode node, int prop) {
        String token = node.tokenRefs.get(prop);
        if (token != null) {
            return token;
        }
        Integer bits = node.properties.get(prop);
        if (bits == null) {
            return "?";
        }
        return switch (ValueTypes.forProperty(prop)) {
            case ValueTypes.STRING -> {
                String value = node.strings.get(prop);
                yield value == null ? "?" : quote(value);
            }
            case ValueTypes.COLOR -> String.format("#%08X", bits);
            case ValueTypes.U8 -> isBoolProp(prop) ? Boolean.toString(bits != 0) : Integer.toString(bits);
            case ValueTypes.U32 -> isMaskProp(prop)
                    ? String.format("0x%08X", bits)
                    : Integer.toString(bits);
            default -> formatF32(Float.intBitsToFloat(bits), prop);
        };
    }

    private static String formatF32(float value, int prop) {
        if (prop == Properties.WIDTH || prop == Properties.HEIGHT) {
            if (value == -1.0f) {
                return "FILL";
            }
            if (value == -2.0f) {
                return "HUG";
            }
        }
        String name = enumName(prop, value);
        if (name != null) {
            return name;
        }
        return Format.f32(value);
    }

    private static String enumName(int prop, float value) {
        int code = Math.round(value);
        return switch (prop) {
            case Properties.ALIGNMENT -> switch (code) {
                case 0 -> "Leading";
                case 1 -> "Center";
                case 2 -> "Trailing";
                case 3 -> "Fill";
                default -> null;
            };
            case Properties.TEXT_ALIGNMENT -> switch (code) {
                case 0 -> "Leading";
                case 1 -> "Center";
                case 2 -> "Trailing";
                default -> null;
            };
            case Properties.TRUNCATION_MODE -> switch (code) {
                case 0 -> "Head";
                case 1 -> "Middle";
                case 2 -> "Tail";
                default -> null;
            };
            case Properties.SHAPE_KIND -> switch (code) {
                case 0 -> "Circle";
                case 1 -> "Rectangle";
                case 2 -> "RoundedRectangle";
                case 3 -> "Capsule";
                case 4 -> "Ellipse";
                case 5 -> "Path";
                default -> null;
            };
            case Properties.CONTENT_MODE -> switch (code) {
                case 0 -> "Fit";
                case 1 -> "Fill";
                default -> null;
            };
            case Properties.TRANSITION -> switch (code) {
                case 0 -> "None";
                case 1 -> "PlatformDefault";
                case 2 -> "Fade";
                case 3 -> "Slide";
                case 4 -> "Scale";
                default -> null;
            };
            case Properties.NAV_CHROME -> switch (code) {
                case 0 -> "PlatformDefault";
                case 1 -> "Custom";
                default -> null;
            };
            case Properties.TOGGLE_STYLE -> switch (code) {
                case 0 -> "Switch";
                case 1 -> "Checkbox";
                case 2 -> "Button";
                default -> null;
            };
            case Properties.CONTROL_SIZE -> switch (code) {
                case 0 -> "Small";
                case 1 -> "Regular";
                case 2 -> "Large";
                default -> null;
            };
            case Properties.DATE_PICKER_MODE -> switch (code) {
                case 0 -> "Date";
                case 1 -> "Time";
                case 2 -> "DateAndTime";
                default -> null;
            };
            case Properties.PICKER_STYLE -> switch (code) {
                case 0 -> "Menu";
                case 1 -> "Segmented";
                case 2 -> "Wheel";
                case 3 -> "RadioGroup";
                default -> null;
            };
            case Properties.FONT_STYLE -> switch (code) {
                case 0 -> "Normal";
                case 1 -> "Italic";
                default -> null;
            };
            case Properties.FONT_DESIGN -> switch (code) {
                case 0 -> "Default";
                case 1 -> "Serif";
                case 2 -> "Rounded";
                case 3 -> "Monospaced";
                default -> null;
            };
            case Properties.TEXT_CASE -> switch (code) {
                case 0 -> "None";
                case 1 -> "Uppercase";
                case 2 -> "Lowercase";
                default -> null;
            };
            default -> null;
        };
    }

    private static boolean isBoolProp(int prop) {
        return prop == Properties.SELECTED
                || prop == Properties.IS_SECURE
                || prop == Properties.IS_INDETERMINATE
                || prop == Properties.VISIBLE
                || prop == Properties.ENABLED
                || prop == Properties.CLIPS_TO_BOUNDS
                || prop == Properties.UNDERLINE
                || prop == Properties.STRIKETHROUGH
                || prop == Properties.COLOR_INVERT
                || prop == Properties.ALLOWS_HIT_TESTING
                || prop == Properties.FIXED_SIZE_HORIZONTAL
                || prop == Properties.FIXED_SIZE_VERTICAL;
    }

    private static boolean isMaskProp(int prop) {
        return prop == Properties.EVENT_LISTENERS || prop == Properties.BORDER_EDGES;
    }

    private static String scrub(String value) {
        String out = value.replace("--", "- -");
        if (out.endsWith("-")) {
            out += " ";
        }
        return out;
    }

    /** Rust's {@code {:?}} string quoting. */
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(ch);
            }
        }
        return out.append('"').toString();
    }
}
