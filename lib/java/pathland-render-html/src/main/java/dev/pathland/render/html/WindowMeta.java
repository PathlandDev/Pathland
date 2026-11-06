package dev.pathland.render.html;

/** Decoded {@code META::SET_WINDOW_METADATA} fields (spec/OPCODE.md §Window metadata). */
final class WindowMeta {
    static final int TITLE = 0x0001;
    static final int DESCRIPTION = 0x0002;
    static final int LOCALE = 0x0003;

    String title;
    String description;
    String locale;

    void set(int field, String value) {
        String stored = value.isEmpty() ? null : value;
        switch (field) {
            case TITLE -> title = stored;
            case DESCRIPTION -> description = stored;
            case LOCALE -> locale = stored;
            default -> {
                // unknown field: ignored
            }
        }
    }
}
