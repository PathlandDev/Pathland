package dev.pathland.server;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validation for the client-supplied per-window id ({@code wid}) — the persisted-state
 * scope. The id is a capability carried on the WebSocket query string, so it must stay
 * bounded and collision-safe with the app-qualified state scope ({@code mount:wid}):
 * a colon is deliberately not allowed (it is the mount separator), and the length is
 * capped so a hostile client cannot bloat state-store keys.
 *
 * <p>An invalid id is treated as absent: SSR renders defaults and a WebSocket connection
 * gets a fresh random scope.
 */
public final class WindowIds {

    /** The maximum accepted length (the client generates a UUID). */
    public static final int MAX_LENGTH = 128;

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]+");

    private WindowIds() {}

    /** Whether {@code wid} is a syntactically valid persisted-state scope. */
    public static boolean isValid(String wid) {
        return wid != null
                && !wid.isEmpty()
                && wid.length() <= MAX_LENGTH
                && VALID.matcher(wid).matches();
    }

    /** The given id when valid, else a fresh random one. */
    public static String orRandom(String wid) {
        return isValid(wid) ? wid : UUID.randomUUID().toString();
    }
}
