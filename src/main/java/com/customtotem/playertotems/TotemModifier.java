package com.customtotem.playertotems;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Parses and represents cosmetic modifiers and easter eggs for Totems of Undying.
 *
 * <p>Supported formats when renaming a totem in an Anvil:
 * <ul>
 *   <li><b>Classic Easter Eggs:</b> {@code "jeb_"}, {@code "Dinnerbone"}, {@code "Grumm"}</li>
 *   <li><b>Player with Easter Egg Tags:</b> {@code "<Player>#jeb_"}, {@code "<Player>#dinnerbone"}, {@code "<Player>#grumm"}</li>
 *   <li><b>Combined Tags:</b> {@code "<Player>#jeb_#dinnerbone"} (rainbow AND upside down!)</li>
 *   <li><b>Contextual Tags (no player name):</b> {@code "#jeb_"}, {@code "#dinnerbone"} (applies to holder/viewer's skin)</li>
 *   <li><b>Alternative Delimiters:</b> Also supports colon e.g. {@code "<Player>:jeb_"}</li>
 * </ul>
 */
public record TotemModifier(String playerName, boolean isRainbow, boolean isUpsideDown) {
    public static final TotemModifier EMPTY = new TotemModifier(null, false, false);
    private static final TotemModifier JEB_STANDALONE = new TotemModifier("jeb_", true, false);

    private static final Pattern TAG_SPLIT_PATTERN = Pattern.compile("[#:]");
    private static final int MAX_CACHE_SIZE = 256;
    private static final Map<String, TotemModifier> PARSE_CACHE = new ConcurrentHashMap<>();

    public boolean isEmpty() {
        return playerName == null && !isRainbow && !isUpsideDown;
    }

    /**
     * Fast check for whether a string is a valid 3-16 character Minecraft username.
     */
    public static boolean isValidUsername(String s) {
        if (s == null) return false;
        int len = s.length();
        if (len < 3 || len > 16) return false;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_';
            if (!valid) return false;
        }
        return true;
    }

    /**
     * Fast check for easter-egg modifiers on a real player's own username (when holding an unrenamed totem).
     * Avoids allocating or caching normal player usernames when no easter egg applies.
     */
    public static TotemModifier fromHolderUsername(String username) {
        if (username == null || username.isEmpty()) return EMPTY;
        if (username.equalsIgnoreCase("dinnerbone") || username.equalsIgnoreCase("grumm")) {
            return new TotemModifier(username, false, true);
        }
        if (username.equalsIgnoreCase("jeb_") || username.equalsIgnoreCase("jeb")) {
            return JEB_STANDALONE;
        }
        return EMPTY;
    }

    /**
     * Parses an anvil rename text or entity name into a {@link TotemModifier}.
     */
    public static TotemModifier parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }

        TotemModifier cached = PARSE_CACHE.get(raw);
        if (cached != null) {
            return cached;
        }

        TotemModifier parsed = parseUncached(raw);
        if (PARSE_CACHE.size() >= MAX_CACHE_SIZE) {
            PARSE_CACHE.clear();
        }
        PARSE_CACHE.put(raw, parsed);
        return parsed;
    }

    private static TotemModifier parseUncached(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return EMPTY;
        }

        // 1. Classic standalone vanilla easter eggs
        if (trimmed.equalsIgnoreCase("jeb_") || trimmed.equalsIgnoreCase("jeb")) {
            return JEB_STANDALONE;
        }
        if (trimmed.equalsIgnoreCase("dinnerbone") || trimmed.equalsIgnoreCase("grumm")) {
            return new TotemModifier(trimmed, false, true);
        }

        // 2. Tagged syntax: split by '#' or ':' only when a delimiter is present
        // Examples: "DjElectrobox#jeb_", "DjElectrobox:dinnerbone", "#jeb_", "Player#jeb_#dinnerbone"
        if (trimmed.indexOf('#') >= 0 || trimmed.indexOf(':') >= 0) {
            String[] tokens = TAG_SPLIT_PATTERN.split(trimmed);
            String baseName = tokens.length > 0 ? tokens[0].trim() : "";
            boolean rainbow = false;
            boolean upsideDown = false;

            for (int i = 1; i < tokens.length; i++) {
                String tag = tokens[i].trim().toLowerCase();
                if (tag.equals("jeb_") || tag.equals("jeb") || tag.equals("rainbow")) {
                    rainbow = true;
                } else if (tag.equals("dinnerbone") || tag.equals("grumm") || tag.equals("flip") || tag.equals("upsidedown")) {
                    upsideDown = true;
                }
            }

            String validatedPlayer = null;
            if (isValidUsername(baseName)) {
                validatedPlayer = baseName;
                if (baseName.equalsIgnoreCase("jeb_") || baseName.equalsIgnoreCase("jeb")) {
                    rainbow = true;
                }
                if (baseName.equalsIgnoreCase("dinnerbone") || baseName.equalsIgnoreCase("grumm")) {
                    upsideDown = true;
                }
            }

            return new TotemModifier(validatedPlayer, rainbow, upsideDown);
        }

        // 3. Regular Minecraft username (3-16 alphanumeric + _)
        if (isValidUsername(trimmed)) {
            return new TotemModifier(trimmed, false, false);
        }

        return EMPTY;
    }
}
