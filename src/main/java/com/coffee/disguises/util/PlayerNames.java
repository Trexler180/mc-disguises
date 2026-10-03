package com.coffee.disguises.util;

/**
 * Validation for names that end up inside a fake player {@code GameProfile}.
 *
 * The client decodes profile names with a 16-character limit and disconnects on
 * anything longer, so an over-length name in a player-disguise spawn kicks every
 * observer in range — and keeps kicking them for as long as the disguise (or its
 * persisted copy) exists.  Commands reject bad names up front with
 * {@link #isValid}; {@link #forProfile} is the last line of defence for names
 * that arrive by other routes (the public API, old persisted files).
 */
public final class PlayerNames {

    /** Maximum profile-name length the client will accept. */
    public static final int MAX_LENGTH = 16;

    private static final String FALLBACK = "Player";

    private PlayerNames() {}

    /**
     * Whether {@code name} is safe to use as a disguise name: 1–16 characters,
     * no whitespace, control characters, or section signs (legacy formatting codes).
     */
    public static boolean isValid(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_LENGTH) return false;
        for (int i = 0; i < name.length(); i++) {
            if (!isAllowedChar(name.charAt(i))) return false;
        }
        return true;
    }

    /**
     * Coerces any name into one that is safe to put on the wire: disallowed
     * characters are dropped and the result is cut to {@link #MAX_LENGTH}.
     * Returns a generic fallback if nothing usable is left.
     */
    public static String forProfile(String name) {
        if (name == null) return FALLBACK;
        StringBuilder out = new StringBuilder(Math.min(name.length(), MAX_LENGTH));
        for (int i = 0; i < name.length() && out.length() < MAX_LENGTH; i++) {
            char c = name.charAt(i);
            if (isAllowedChar(c)) out.append(c);
        }
        return out.isEmpty() ? FALLBACK : out.toString();
    }

    private static boolean isAllowedChar(char c) {
        return c > ' ' && c != '\u007F' && c != '§' && !Character.isWhitespace(c);
    }
}
