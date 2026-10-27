package dev.pathland.view;

/**
 * The renderer-wide color-scheme mode (`META::SET_COLOR_SCHEME`,
 * spec/TOKENS.md). {@link #PLATFORM} follows the OS/browser light-dark signal;
 * {@link #LIGHT}/{@link #DARK} force the effective scheme until {@code PLATFORM}
 * is restored.
 */
public enum ThemeMode {
    /** Follow the platform's native light/dark signal. */
    PLATFORM(0),
    /** Force the light design. */
    LIGHT(1),
    /** Force the dark design. */
    DARK(2);

    private final int code;

    ThemeMode(int code) {
        this.code = code;
    }

    /** The wire {@code A} value of {@code META::SET_COLOR_SCHEME}. */
    public int code() {
        return code;
    }
}
