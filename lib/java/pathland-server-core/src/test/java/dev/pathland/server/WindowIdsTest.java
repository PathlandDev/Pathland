package dev.pathland.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The persisted-state scope id is bounded and cannot collide with the mount separator. */
class WindowIdsTest {

    @Test
    void acceptsUUIDsAndSimpleIds() {
        assertTrue(WindowIds.isValid("952b4e5b-4df9-4bc0-a871-dd86dd4c98ca"));
        assertTrue(WindowIds.isValid("w1"));
        assertTrue(WindowIds.isValid("shared-wid_2.0"));
    }

    @Test
    void rejectsEmptyPathLikeColonAndOversizedIds() {
        assertFalse(WindowIds.isValid(null));
        assertFalse(WindowIds.isValid(""));
        assertFalse(WindowIds.isValid("has space"));
        assertFalse(WindowIds.isValid("../../etc/passwd"));
        assertFalse(WindowIds.isValid("/app2:x"), "a colon would collide with the mount scope separator");
        assertFalse(WindowIds.isValid("w".repeat(WindowIds.MAX_LENGTH + 1)));
    }

    @Test
    void invalidIdsGetAFreshScope() {
        String fresh = WindowIds.orRandom("bad id");
        assertTrue(WindowIds.isValid(fresh));
        assertEquals("w1", WindowIds.orRandom("w1"));
    }
}
