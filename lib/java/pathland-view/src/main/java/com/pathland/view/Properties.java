package com.pathland.view;

/** Protocol property ids (mirrors {@code pathland_core::property_id}). */
public final class Properties {

    // Stack
    public static final int SPACING = 0x0001;
    public static final int ALIGNMENT = 0x0002;
    public static final int CONTENT_MARGINS = 0x0005;
    // Shape
    public static final int SHAPE_KIND = 0x0006;
    // Text
    public static final int TEXT = 0x000A;
    public static final int LINE_LIMIT = 0x000B;
    public static final int TEXT_ALIGNMENT = 0x000C;
    public static final int TRUNCATION_MODE = 0x000D;
    // Layout (0x000E–0x001D)
    public static final int OFFSET_X = 0x000E;
    public static final int OFFSET_Y = 0x000F;
    public static final int POSITION_X = 0x0010;
    public static final int POSITION_Y = 0x0011;
    public static final int MIN_WIDTH = 0x0012;
    public static final int IDEAL_WIDTH = 0x0013;
    public static final int MAX_WIDTH = 0x0014;
    public static final int MIN_HEIGHT = 0x0015;
    public static final int IDEAL_HEIGHT = 0x0016;
    public static final int MAX_HEIGHT = 0x0017;
    public static final int FIXED_SIZE_HORIZONTAL = 0x0018;
    public static final int FIXED_SIZE_VERTICAL = 0x0019;
    public static final int LAYOUT_PRIORITY = 0x001A;
    public static final int ASPECT_RATIO = 0x001B;
    public static final int CONTENT_MODE = 0x001C;
    public static final int MINIMUM_SCALE_FACTOR = 0x001D;
    // Style (0x1000 range)
    public static final int BACKGROUND_COLOR = 0x1001;
    public static final int IMAGE_SOURCE = 0x1002;
    public static final int AUDIO_SOURCE = 0x1033;
    public static final int VIDEO_SOURCE = 0x1034;
    public static final int BORDER_WIDTH = 0x1003;
    public static final int BORDER_COLOR = 0x1004;
    public static final int BORDER_RADIUS = 0x1005;
    public static final int FONT_SIZE = 0x1007;
    public static final int FONT_WEIGHT = 0x1008;
    public static final int FONT_FAMILY = 0x1009;
    public static final int COLOR = 0x100A;
    public static final int WIDTH = 0x100B;
    public static final int HEIGHT = 0x100C;
    public static final int OPACITY = 0x100D;
    public static final int VISIBLE = 0x100E;
    public static final int Z_INDEX = 0x100F;
    public static final int CLIPS_TO_BOUNDS = 0x1010;
    public static final int PADDING = 0x1011;
    public static final int PADDING_TOP = 0x1012;
    public static final int PADDING_RIGHT = 0x1013;
    public static final int PADDING_BOTTOM = 0x1014;
    public static final int PADDING_LEFT = 0x1015;
    public static final int BORDER_EDGES = 0x1016;
    // Text format & effects (0x1017–0x102F)
    public static final int FONT_STYLE = 0x1017;
    public static final int FONT_DESIGN = 0x1018;
    public static final int FONT_WIDTH = 0x1019;
    public static final int KERNING = 0x101A;
    public static final int TRACKING = 0x101B;
    public static final int BASELINE_OFFSET = 0x101C;
    public static final int LINE_SPACING = 0x101D;
    public static final int TEXT_CASE = 0x101E;
    public static final int UNDERLINE = 0x101F;
    public static final int STRIKETHROUGH = 0x1020;
    public static final int SHADOW_COLOR = 0x1021;
    public static final int SHADOW_RADIUS = 0x1022;
    public static final int SHADOW_X = 0x1023;
    public static final int SHADOW_Y = 0x1024;
    public static final int BLUR_RADIUS = 0x1025;
    public static final int SATURATION = 0x1026;
    public static final int CONTRAST = 0x1027;
    public static final int BRIGHTNESS = 0x1028;
    public static final int GRAYSCALE = 0x1029;
    public static final int HUE_ROTATION = 0x102A;
    public static final int COLOR_MULTIPLY = 0x102B;
    public static final int COLOR_INVERT = 0x102C;
    public static final int ROTATION_DEGREES = 0x102D;
    public static final int SCALE = 0x102E;
    public static final int ALLOWS_HIT_TESTING = 0x102F;
    public static final int TINT = 0x1030;
    /** Presentation hint for structural swaps (F32 enum code): None=0, PlatformDefault=1, Fade=2, Slide=3, Scale=4. */
    public static final int TRANSITION = 0x1031;
    /** Predefined typography (F32 enum code, see {@link TextStyle}): the heading
     *  styles imply a heading element on a {@code TEXT}; raw font modifiers
     *  override the visual on top and never imply a heading. */
    public static final int TEXT_STYLE = 0x1032;
    // Semantic (0x2000 range)
    public static final int ROLE = 0x2001;
    public static final int STATE = 0x2002;
    public static final int ENABLED = 0x2003;
    public static final int SELECTED = 0x2004;
    public static final int EVENT_LISTENERS = 0x2005;
    public static final int VALUE = 0x2006;
    public static final int MIN_VALUE = 0x2007;
    public static final int MAX_VALUE = 0x2008;
    public static final int LABEL = 0x200A;
    public static final int PROMPT = 0x200B;
    // Control properties
    public static final int STEP_VALUE = 0x2009;
    public static final int CONTROL_SIZE = 0x200C;
    public static final int IS_SECURE = 0x200D;
    public static final int PROGRESS = 0x200E;
    public static final int IS_INDETERMINATE = 0x200F;
    public static final int SELECTION = 0x2010;
    public static final int COLOR_VALUE = 0x2012;
    public static final int DATE_PICKER_MODE = 0x2013;
    public static final int PICKER_STYLE = 0x2014;
    // Binding/action
    public static final int ACTION_ID = 0x2016;
    public static final int BINDING_ID = 0x2017;
    public static final int TOGGLE_STYLE = 0x2018;
    // Navigation
    public static final int ROUTE = 0x2019;
    public static final int NAV_DEPTH = 0x201A;
    public static final int NAV_CHROME = 0x201B;

    private Properties() {}
}