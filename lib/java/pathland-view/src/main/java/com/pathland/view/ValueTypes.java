package com.pathland.view;

/** Value types for {@code STYLE::SET_PROPERTY} (mirrors {@code pathland_core::value_type}). */
public final class ValueTypes {

    public static final int U8 = 0x01;
    public static final int U32 = 0x02;
    public static final int I32 = 0x03;
    public static final int F32 = 0x04;
    public static final int STRING = 0x05;
    public static final int ENUM = 0x06;
    public static final int COLOR = 0x07;
    public static final int DESIGN_TOKEN = 0x08;

    /** The protocol value type for a property id (mirrors {@code value_type_for}). */
    public static int forProperty(int prop) {
        return switch (prop) {
            case Properties.COLOR, Properties.BACKGROUND_COLOR, Properties.BORDER_COLOR,
                    Properties.COLOR_MULTIPLY, Properties.SHADOW_COLOR, Properties.COLOR_VALUE,
                    Properties.TINT -> COLOR;
            case Properties.EVENT_LISTENERS, Properties.BORDER_EDGES -> U32;
            case Properties.SELECTED, Properties.VISIBLE, Properties.ENABLED, Properties.UNDERLINE,
                    Properties.STRIKETHROUGH, Properties.COLOR_INVERT, Properties.CLIPS_TO_BOUNDS,
                    Properties.ALLOWS_HIT_TESTING, Properties.IS_SECURE, Properties.IS_INDETERMINATE,
                    Properties.FIXED_SIZE_HORIZONTAL, Properties.FIXED_SIZE_VERTICAL -> U8;
            case Properties.LABEL, Properties.PROMPT, Properties.FONT_FAMILY, Properties.IMAGE_SOURCE,
                    Properties.AUDIO_SOURCE, Properties.VIDEO_SOURCE, Properties.ROUTE -> STRING;
            case Properties.LINE_LIMIT, Properties.SELECTION, Properties.ACTION_ID, Properties.BINDING_ID,
                    Properties.NAV_DEPTH -> U32;
            // Enums ride the wire as F32 holding the numeric enum code (spec convention).
            case Properties.ALIGNMENT, Properties.TEXT_ALIGNMENT, Properties.TRUNCATION_MODE,
                    Properties.TEXT_CASE, Properties.FONT_STYLE, Properties.FONT_DESIGN,
                    Properties.CONTENT_MODE, Properties.CONTROL_SIZE, Properties.SHAPE_KIND,
                    Properties.DATE_PICKER_MODE, Properties.PICKER_STYLE, Properties.TOGGLE_STYLE -> F32;
            default -> F32;
        };
    }

    private ValueTypes() {}
}