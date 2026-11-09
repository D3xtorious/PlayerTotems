package com.customtotem.playertotems;

import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the most recent totem consumed by death protection for each entity.
 * This ensures that when the totem pop activation animation is triggered on the client,
 * the original item with all its components (custom name, profile) can be retrieved,
 * even after the stack has been shrunk or removed from the player's inventory.
 *
 * <p>Contains NO client-only imports and is completely safe to run on dedicated servers,
 * integrated singleplayer servers, and clients.</p>
 */
public class TotemPopTracker {
    public record PoppedTotemEntry(ItemStack stack, long timestamp) {}

    private static final Map<UUID, PoppedTotemEntry> RECENT_POPS = new ConcurrentHashMap<>();
    private static final long EXPIRATION_MS = 5000L; // 5 seconds expiration

    /**
     * Records a totem right before it is shrunk or consumed by death protection.
     */
    public static void recordPoppedTotem(UUID entityUuid, ItemStack stack) {
        if (entityUuid == null || stack == null || stack.isEmpty()) return;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        RECENT_POPS.put(entityUuid, new PoppedTotemEntry(copy, System.currentTimeMillis()));
    }

    /**
     * Retrieves the recorded popped totem for an entity if not expired.
     */
    public static ItemStack getRecordedPoppedTotem(UUID entityUuid) {
        if (entityUuid == null) return null;
        PoppedTotemEntry entry = RECENT_POPS.get(entityUuid);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.timestamp() > EXPIRATION_MS) {
            RECENT_POPS.remove(entityUuid);
            return null;
        }
        return entry.stack().copy();
    }

    /**
     * Clears tracked pops.
     */
    public static void clear() {
        RECENT_POPS.clear();
    }
}
