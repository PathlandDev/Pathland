package com.pathland.view;

/** Commands per category (mirrors {@code pathland_core::tree/style/event/meta}). */
public final class Commands {

    private Commands() {}

    /** TREE commands. */
    public static final class Tree {
        public static final int CREATE_NODE = 0x01;
        public static final int DELETE_NODE = 0x02;
        public static final int INSERT_CHILD = 0x03;
        public static final int REMOVE_CHILD = 0x04;
        public static final int MOVE_CHILD = 0x05;
        private Tree() {}
    }

    /** PARAMETER commands. */
    public static final class Parameter {
        public static final int SET_PROPERTY = 0x01;
        public static final int SET_DESIGN_TOKEN = 0x02;
        public static final int SET_TEXT = 0x03;
        public static final int SET_DATE = 0x04;
        private Parameter() {}
    }

    /** EVENT commands (host → guest raw inputs). */
    public static final class Event {
        public static final int POINTER_DOWN = 0x01;
        public static final int POINTER_MOVE = 0x02;
        public static final int POINTER_UP = 0x03;
        public static final int KEY_DOWN = 0x04;
        public static final int KEY_UP = 0x05;
        public static final int VALUE_CHANGED = 0x06;
        public static final int TEXT_CHANGED = 0x07;
        public static final int FOCUS_CHANGED = 0x08;
        public static final int EDITING_CHANGED = 0x09;
        public static final int SUBMIT = 0x0A;
        public static final int SCROLL = 0x0B;
        public static final int WHEEL = 0x0C;
        public static final int DATE_CHANGED = 0x0D;
        /** Global navigation request (host → guest, never node-keyed); URL in the string section when `NAVIGATE_URL` is set. */
        public static final int NAVIGATE = 0x0E;
        /** A bound `AUDIO`/`VIDEO` node's playback state changed (B = 0/1). */
        public static final int MEDIA_PLAY_STATE_CHANGED = 0x0F;
        /** A bound media node's current position (B = seconds, f32). */
        public static final int MEDIA_TIME_UPDATED = 0x10;
        /** A bound media node reached the end of its media. */
        public static final int MEDIA_ENDED = 0x11;
        /** A bound media node's volume changed (B = 0..1, f32). */
        public static final int MEDIA_VOLUME_CHANGED = 0x12;
        /** A `SIZE_THAT_FITS` slot's selected candidate changed (B = index, f32). */
        public static final int FIT_CHANGED = 0x13;
        private Event() {}
    }

    /** META commands. */
public static final class Meta {
            public static final int RESET = 0x01;
            /** Platform environment field (host → guest): `A=fieldId, B=value`. See {@link Environment}. */
            public static final int ENVIRONMENT = 0x02;
            /** The host (renderer) requests a full snapshot of the current tree (host → guest). */
            public static final int RESYNC = 0x03;
            /** A transport-liveness heartbeat probe (guest → host): `A/B/C = 0`. Network batch transport only (spec/OPCODE.md §Transport). */
            public static final int PING = 0x04;
            /** The reply to {@link #PING} (host → guest): `A/B/C = 0`. Network batch transport only. */
            public static final int PONG = 0x05;
            private Meta() {}
        }

    /** `META::ENVIRONMENT` field ids (spec/OPCODE.md §Environment fields). */
    public static final class Environment {
        public static final int VIEWPORT_WIDTH = 0x0001;
        public static final int VIEWPORT_HEIGHT = 0x0002;
        public static final int ROUTE = 0x0003;
        private Environment() {}
    }

    /** Flag bits shared across categories. */
    public static final class Flags {
        public static final int POINTER_SECONDARY = 0x0001;
        public static final int HOVER_ENTER = 0x0001;
        public static final int HOVER_LEAVE = 0x0002;
        public static final int KEY_REPEAT = 0x0002;
        /** `NAVIGATE`: the URL in the string section; without it the event is a back request. */
        public static final int NAVIGATE_URL = 0x0001;
        private Flags() {}
    }

    /** Bits for the {@code EVENT_LISTENERS} semantic property. */
    public static final class Listeners {
        public static final int POINTER_DOWN = 1 << 0;
        public static final int POINTER_MOVE = 1 << 1;
        public static final int POINTER_UP = 1 << 2;
        public static final int KEY_DOWN = 1 << 3;
        public static final int KEY_UP = 1 << 4;
        public static final int FOCUS = 1 << 5;
        public static final int EDITING = 1 << 6;
        public static final int SUBMIT = 1 << 7;
        public static final int SCROLL = 1 << 8;
        public static final int WHEEL = 1 << 9;
        public static final int POINTER = POINTER_DOWN | POINTER_MOVE | POINTER_UP;
        private Listeners() {}
    }

    /** Special size constants for WIDTH / HEIGHT. */
    public static final class Size {
        public static final float FILL = -1f;
        public static final float HUG_CONTENT = -2f;
        private Size() {}
    }
}