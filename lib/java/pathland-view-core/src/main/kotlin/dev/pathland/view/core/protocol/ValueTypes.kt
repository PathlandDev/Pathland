package dev.pathland.view.core.protocol

/** Value types for `PARAMETER::SET_PROPERTY` (mirrors `pathland_core::value_type`). */
object ValueTypes {
    const val U8 = 0x01
    const val U32 = 0x02
    const val I32 = 0x03
    const val F32 = 0x04
    const val STRING = 0x05
    const val ENUM = 0x06
    const val COLOR = 0x07
    const val DESIGN_TOKEN = 0x08
    const val LIST = 0x09

    /** The protocol value type for a property id (mirrors `value_type_for`). */
    @JvmStatic
    fun forProperty(prop: Int): Int = when (prop) {
        Properties.COLOR, Properties.BACKGROUND_COLOR, Properties.BORDER_COLOR,
        Properties.COLOR_MULTIPLY, Properties.SHADOW_COLOR, Properties.COLOR_VALUE,
        Properties.TINT -> COLOR
        Properties.EVENT_LISTENERS, Properties.BORDER_EDGES -> U32
        Properties.SELECTED, Properties.VISIBLE, Properties.ENABLED, Properties.UNDERLINE,
        Properties.STRIKETHROUGH, Properties.COLOR_INVERT, Properties.CLIPS_TO_BOUNDS,
        Properties.ALLOWS_HIT_TESTING, Properties.IS_SECURE, Properties.IS_INDETERMINATE,
        Properties.FIXED_SIZE_HORIZONTAL, Properties.FIXED_SIZE_VERTICAL,
        Properties.MEDIA_MUTED, Properties.MEDIA_LOOP -> U8
        Properties.LABEL, Properties.PROMPT, Properties.FONT_FAMILY, Properties.IMAGE_SOURCE,
        Properties.MEDIA_SOURCE, Properties.ROUTE,
        Properties.ICON_NAME, Properties.GRID_TRACKS -> STRING
        Properties.LINE_LIMIT, Properties.SELECTION, Properties.ACTION_ID, Properties.BINDING_ID,
        Properties.NAV_DEPTH, Properties.PLAYBACK_STATE,
        Properties.MEDIA_CONTROLLER_ID -> U32
        Properties.FIT_QUERY -> LIST
        Properties.ALIGNMENT, Properties.TEXT_ALIGNMENT, Properties.TRUNCATION_MODE,
        Properties.TEXT_CASE, Properties.FONT_STYLE, Properties.FONT_DESIGN,
        Properties.CONTENT_MODE, Properties.CONTROL_SIZE, Properties.SHAPE_KIND,
        Properties.DATE_PICKER_MODE, Properties.PICKER_STYLE, Properties.TOGGLE_STYLE -> F32
        else -> F32
    }
}
