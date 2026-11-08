package dev.pathland.core.protocol;

/** Value types for {@code PARAMETER::SET_PROPERTY} (mirrors {@code pathland_core::value_type}). */
public final class ValueTypes {
    public static final int U8 = 0x01;
    public static final int U32 = 0x02;
    public static final int I32 = 0x03;
    public static final int F32 = 0x04;
    public static final int STRING = 0x05;
    public static final int ENUM = 0x06;
    public static final int COLOR = 0x07;
    public static final int DESIGN_TOKEN = 0x08;
    public static final int LIST = 0x09;

    private ValueTypes() {
    }

    /** The protocol value type for a property id (mirrors {@code value_type_for}). */
    public static int forProperty(int prop) {
        switch (prop) {
            case Properties.COLOR:
            case Properties.BACKGROUND_COLOR:
            case Properties.BORDER_COLOR:
            case Properties.COLOR_MULTIPLY:
            case Properties.SHADOW_COLOR:
            case Properties.COLOR_VALUE:
            case Properties.TINT:
                return COLOR;
            case Properties.EVENT_LISTENERS:
            case Properties.BORDER_EDGES:
                return U32;
            case Properties.SELECTED:
            case Properties.VISIBLE:
            case Properties.ENABLED:
            case Properties.UNDERLINE:
            case Properties.STRIKETHROUGH:
            case Properties.COLOR_INVERT:
            case Properties.CLIPS_TO_BOUNDS:
            case Properties.ALLOWS_HIT_TESTING:
            case Properties.IS_SECURE:
            case Properties.IS_INDETERMINATE:
            case Properties.FIXED_SIZE_HORIZONTAL:
            case Properties.FIXED_SIZE_VERTICAL:
            case Properties.MEDIA_MUTED:
            case Properties.MEDIA_LOOP:
                return U8;
            case Properties.LABEL:
            case Properties.PROMPT:
            case Properties.FONT_FAMILY:
            case Properties.IMAGE_SOURCE:
            case Properties.MEDIA_SOURCE:
            case Properties.ROUTE:
            case Properties.ICON_NAME:
            case Properties.GRID_TRACKS:
                return STRING;
            case Properties.LINE_LIMIT:
            case Properties.SELECTION:
            case Properties.ACTION_ID:
            case Properties.BINDING_ID:
            case Properties.NAV_DEPTH:
            case Properties.PLAYBACK_STATE:
            case Properties.MEDIA_CONTROLLER_ID:
                return U32;
            case Properties.FIT_QUERY:
                return LIST;
            case Properties.ALIGNMENT:
            case Properties.TEXT_ALIGNMENT:
            case Properties.TRUNCATION_MODE:
            case Properties.TEXT_CASE:
            case Properties.FONT_STYLE:
            case Properties.FONT_DESIGN:
            case Properties.CONTENT_MODE:
            case Properties.CONTROL_SIZE:
            case Properties.SHAPE_KIND:
            case Properties.DATE_PICKER_MODE:
            case Properties.PICKER_STYLE:
            case Properties.TOGGLE_STYLE:
                return F32;
            default:
                return F32;
        }
    }
}
