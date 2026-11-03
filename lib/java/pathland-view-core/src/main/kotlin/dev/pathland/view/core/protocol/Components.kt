package dev.pathland.view.core.protocol

/**
 * Protocol component type ids (mirrors {@code pathland_core::component_type} and the
 * grouped ranges in {@code spec/PRIMITIVES.md}).
 */
object Components {
    const val TEXT = 0x01
    const val IMAGE = 0x02
    const val COLOR = 0x03
    const val SHAPE = 0x04
    const val DIVIDER = 0x05
    const val SPACER = 0x06
    const val PROGRESS_VIEW = 0x07
    const val GAUGE = 0x08
    const val VIDEO = 0x0A
    const val ICON = 0x0B

    const val VSTACK = 0x10
    const val HSTACK = 0x11
    const val ZSTACK = 0x12
    const val GRID = 0x13
    const val SCROLLVIEW = 0x14
    const val LAZY_VGRID = 0x15
    const val LAZY_HGRID = 0x16
    const val SIZE_THAT_FITS = 0x17
    const val LAZY_VSTACK = 0x1B
    const val LAZY_HSTACK = 0x1C
    const val GRID_ROW = 0x1D
    const val PROXY_VIEW = 0x1E

    const val BUTTON = 0x20
    const val TEXT_FIELD = 0x21
    const val TEXT_EDITOR = 0x22
    const val TOGGLE = 0x24
    const val SLIDER = 0x25
    const val STEPPER = 0x26
    const val DATE_PICKER = 0x27
    const val PICKER = 0x28
    const val MENU = 0x29
    const val COLOR_PICKER = 0x2A

    const val MEDIA_CONTROLLER = 0x30

    const val COMMENT = 0x7F
}
