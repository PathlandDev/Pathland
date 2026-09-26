package com.pathland.view;

/**
 * Protocol component type ids (mirrors {@code pathland_core::component_type} and the
 * grouped ranges in {@code spec/PRIMITIVES.md}).
 *
 * <p>Ranges: {@code 0x01–0x0F} primitive drawing nodes, {@code 0x10–0x1F} layout &
 * container primitives, {@code 0x20–0x2F} semantic control nodes, {@code 0x7F} utility.
 */
public final class Components {

    // ── Primitive Drawing & Visual Nodes (0x01–0x0F) ──────────────────────
    public static final int TEXT = 0x01;
    public static final int IMAGE = 0x02;
    public static final int COLOR = 0x03;
    public static final int SHAPE = 0x04;
    public static final int DIVIDER = 0x05;
    public static final int SPACER = 0x06;
    public static final int PROGRESS_VIEW = 0x07;
    public static final int GAUGE = 0x08;
    public static final int AUDIO = 0x09;
    public static final int VIDEO = 0x0A;

    // ── Layout & Container Primitives (0x10–0x1F) ──────────────────────────
    public static final int VSTACK = 0x10;
    public static final int HSTACK = 0x11;
    public static final int ZSTACK = 0x12;
    public static final int GRID = 0x13;
    public static final int SCROLLVIEW = 0x14;
    public static final int LAZY_VGRID = 0x15;
    public static final int LAZY_HGRID = 0x16;
    public static final int LAZY_VSTACK = 0x1B;
    public static final int LAZY_HSTACK = 0x1C;

    // ── Semantic Control Nodes (0x20–0x2F) ─────────────────────────────────
    public static final int BUTTON = 0x20;
    public static final int TEXT_FIELD = 0x21;
    public static final int TEXT_EDITOR = 0x22;
    public static final int TOGGLE = 0x24;
    public static final int SLIDER = 0x25;
    public static final int STEPPER = 0x26;
    public static final int DATE_PICKER = 0x27;
    public static final int PICKER = 0x28;
    public static final int MENU = 0x29;
    public static final int COLOR_PICKER = 0x2A;

    // ── Utility ────────────────────────────────────────────────────────────
    public static final int COMMENT = 0x7F;

    private Components() {}
}