package com.pathland.view;

/**
 * The canonical icon vocabulary for the {@link Icon} primitive (spec/ICONS.md).
 * These neutral semantic names match {@code pathland-icons} (the Rust catalog);
 * each renderer maps them to its native icon set — Adwaita on GTK, Lucide on the
 * web. The {@code wire()} value is the canonical {@code ICON_NAME} string.
 */
public enum IconName {

    HOME("home"),
    SETTINGS("settings"),
    SEARCH("search"),
    MENU("menu"),
    CLOSE("close"),
    CLOUD("cloud"),
    CHEVRON_LEFT("chevron-left"),
    CHEVRON_RIGHT("chevron-right"),
    CHEVRON_UP("chevron-up"),
    CHEVRON_DOWN("chevron-down"),
    ARROW_LEFT("arrow-left"),
    ARROW_RIGHT("arrow-right"),
    ADD("add"),
    REMOVE("remove"),
    CHECK("check"),
    EDIT("edit"),
    DELETE("delete"),
    SAVE("save"),
    SHARE("share"),
    DOWNLOAD("download"),
    UPLOAD("upload"),
    REFRESH("refresh"),
    PLAY("play"),
    PAUSE("pause"),
    STOP("stop"),
    SKIP_BACK("skip-back"),
    SKIP_FORWARD("skip-forward"),
    VOLUME("volume"),
    VOLUME_MUTE("volume-mute"),
    SHUFFLE("shuffle"),
    REPEAT("repeat"),
    INFO("info"),
    WARNING("warning"),
    ERROR("error"),
    SUCCESS("success"),
    HEART("heart"),
    STAR("star"),
    USER("user"),
    USERS("users"),
    LOCK("lock"),
    LOGOUT("logout"),
    BELL("bell"),
    FOLDER("folder"),
    FILE("file"),
    IMAGE("image"),
    MUSIC("music"),
    GRID("grid"),
    LIST("list"),
    FILTER("filter");

    private final String wire;

    IconName(String wire) {
        this.wire = wire;
    }

    /** The canonical {@code ICON_NAME} value (the shared protocol vocabulary). */
    public String wire() {
        return wire;
    }
}