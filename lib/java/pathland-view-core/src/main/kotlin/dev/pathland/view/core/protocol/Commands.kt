package dev.pathland.view.core.protocol

import dev.pathland.view.core.Platform

/** Commands per category (mirrors {@code pathland_core::tree/style/event/meta}). */
object Commands {

    /** TREE commands. */
    object Tree {
        const val CREATE_NODE = 0x01
        const val DELETE_NODE = 0x02
        const val INSERT_CHILD = 0x03
        const val REMOVE_CHILD = 0x04
        const val MOVE_CHILD = 0x05
    }

    /** PARAMETER commands. */
    object Parameter {
        const val SET_PROPERTY = 0x01
        const val SET_DESIGN_TOKEN = 0x02
        const val SET_TEXT = 0x03
        const val SET_DATE = 0x04
        const val SET_ATTRIBUTED_TEXT = 0x05
    }

    /** EVENT commands (host → guest raw inputs). */
    object Event {
        const val POINTER_DOWN = 0x01
        const val POINTER_MOVE = 0x02
        const val POINTER_UP = 0x03
        const val KEY_DOWN = 0x04
        const val KEY_UP = 0x05
        const val VALUE_CHANGED = 0x06
        const val TEXT_CHANGED = 0x07
        const val FOCUS_CHANGED = 0x08
        const val EDITING_CHANGED = 0x09
        const val SUBMIT = 0x0A
        const val SCROLL = 0x0B
        const val WHEEL = 0x0C
        const val DATE_CHANGED = 0x0D
        /** Global navigation request (host → guest, never node-keyed). */
        const val NAVIGATE = 0x0E
        const val MEDIA_PLAY_STATE_CHANGED = 0x0F
        const val MEDIA_TIME_UPDATED = 0x10
        const val MEDIA_ENDED = 0x11
        const val MEDIA_VOLUME_CHANGED = 0x12
        const val FIT_CHANGED = 0x13
        const val MEDIA_DURATION_CHANGED = 0x14
    }

    /** META commands. */
    object Meta {
        const val RESET = 0x01
        const val ENVIRONMENT = 0x02
        const val RESYNC = 0x03
        const val PING = 0x04
        const val PONG = 0x05
        const val SET_COLOR_SCHEME = 0x06
        const val RESET_THEME = 0x07
        const val SET_WINDOW_METADATA = 0x08
    }

    /** `META::SET_WINDOW_METADATA` field ids. */
    object WindowFields {
        const val TITLE = 0x0001
        const val DESCRIPTION = 0x0002
        const val LOCALE = 0x0003
    }

    /** `META::SET_COLOR_SCHEME` mode values. */
    object ColorSchemeMode {
        const val PLATFORM = 0
        const val LIGHT = 1
        const val DARK = 2
    }

    /** `META::ENVIRONMENT` field ids. */
    object Environment {
        const val VIEWPORT_WIDTH = 0x0001
        const val VIEWPORT_HEIGHT = 0x0002
        const val ROUTE = 0x0003
    }

    /** Flag bits shared across categories. */
    object Flags {
        const val POINTER_SECONDARY = 0x0001
        const val HOVER_ENTER = 0x0001
        const val HOVER_LEAVE = 0x0002
        const val KEY_REPEAT = 0x0002
        const val NAVIGATE_URL = 0x0001
    }

    /** Bits for the {@code EVENT_LISTENERS} semantic property. */
    object Listeners {
        const val POINTER_DOWN = 1 shl 0
        const val POINTER_MOVE = 1 shl 1
        const val POINTER_UP = 1 shl 2
        const val KEY_DOWN = 1 shl 3
        const val KEY_UP = 1 shl 4
        const val FOCUS = 1 shl 5
        const val EDITING = 1 shl 6
        const val SUBMIT = 1 shl 7
        const val SCROLL = 1 shl 8
        const val WHEEL = 1 shl 9
        const val POINTER = POINTER_DOWN or POINTER_MOVE or POINTER_UP
    }

    /** Special size constants for WIDTH / HEIGHT. */
    object Size {
        const val FILL = -1f
        const val HUG_CONTENT = -2f
    }
}
