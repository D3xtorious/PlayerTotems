package com.customtotem.playertotems;

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

    public boolean isEmpty() {
        return playerName == null && !isRainbow && !isUpsideDown;
    }

    /**
     * Parses an anvil rename text or entity name into a {@link TotemModifier}.
     */
    public static TotemModifier parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }

        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase();

        // 1. Classic standalone vanilla easter eggs
        if (lower.equals("jeb_") || lower.equals("jeb")) {
            return new TotemModifier("jeb_", true, false);
        }
        if (lower.equals("dinnerbone") || lower.equals("grumm")) {
            return new TotemModifier(trimmed, false, true);
        }

        // 2. Tagged syntax: split by '#' or ':'
        // Examples: "DjElectrobox#jeb_", "DjElectrobox:dinnerbone", "#jeb_", "Player#jeb_#dinnerbone"
        String[] tokens = trimmed.split("[#:]");
        if (tokens.length > 1 || trimmed.startsWith("#") || trimmed.startsWith(":")) {
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
            if (!baseName.isEmpty() && baseName.matches("^[a-zA-Z0-9_]{3,16}$")) {
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
        if (trimmed.matches("^[a-zA-Z0-9_]{3,16}$")) {
            return new TotemModifier(trimmed, false, false);
        }

        return EMPTY;
    }
}
