package com.customtotem.playertotems.client;

import com.customtotem.playertotems.PlayerTotems;
import com.customtotem.playertotems.TotemModifier;
import com.customtotem.playertotems.TotemPopTracker;
import com.customtotem.playertotems.client.mixin.ItemStackRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles skin resolution, model geometry type (Classic 4px vs Slim 3px),
 * and special model layer injection for Totems of Undying.
 *
 * <p>Supports context-aware resolution (held in hand, dropped on ground, inventory GUI),
 * permanent player binding via Anvil renaming, and commemorative Item Frame displays.</p>
 */
public class PlayerTotemClientHandler {
    private static final Identifier DEFAULT_SKIN = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
    private static final Identifier ALEX_SKIN = Identifier.withDefaultNamespace("textures/entity/player/slim/alex.png");
    public static final SkinData DEFAULT_SKIN_DATA = new SkinData(DEFAULT_SKIN, false);
    private static final SkinData ALEX_SKIN_DATA = new SkinData(ALEX_SKIN, true);
    private static final int MAX_PROFILE_CACHE_SIZE = 256;
    private static final Map<String, ResolvableProfile> UNRESOLVED_PROFILE_CACHE = new ConcurrentHashMap<>();

    /**
     * Skin and geometry layout container.
     * Automatically ensures texture identifier is non-null and converts raw vanilla player asset IDs
     * (e.g. "entity/player/wide/steve") to full texture paths ("textures/entity/player/wide/steve.png")
     * without corrupting dynamic skin paths from skin mods.
     *
     * @param texture Skin texture resource identifier
     * @param isSlim True if the player uses the 3px Alex / Slim model; false for 4px Steve / Classic
     * @param isRainbow True if the totem cycles smoothly through rainbow colors (jeb_ effect)
     * @param isUpsideDown True if the totem is flipped upside down (Dinnerbone / Grumm effect)
     */
    public record SkinData(Identifier texture, boolean isSlim, boolean isRainbow, boolean isUpsideDown) {
        public SkinData {
            if (texture == null) {
                texture = DEFAULT_SKIN;
            } else {
                String path = texture.getPath();
                if (path.startsWith("entity/player/")) {
                    texture = texture.withPath("textures/" + path + (path.endsWith(".png") ? "" : ".png"));
                }
            }
        }

        public SkinData(Identifier texture, boolean isSlim) {
            this(texture, isSlim, false, false);
        }

        public SkinData withModifiers(boolean isRainbow, boolean isUpsideDown) {
            if (this.isRainbow == isRainbow && this.isUpsideDown == isUpsideDown) {
                return this;
            }
            return new SkinData(this.texture, this.isSlim, isRainbow, isUpsideDown);
        }
    }

    /**
     * Extracts SkinData from Minecraft's PlayerSkin record safely.
     * Always utilizes texturePath() (NOT id(), which returns raw asset IDs without textures/ and .png).
     */
    public static SkinData getSkinDataFromPlayerSkin(PlayerSkin skin) {
        if (skin == null || skin.body() == null) {
            return DEFAULT_SKIN_DATA;
        }
        Identifier texture = skin.body().texturePath();
        if (texture == null) {
            return DEFAULT_SKIN_DATA;
        }
        boolean isSlim = skin.model() == PlayerModelType.SLIM;
        return new SkinData(texture, isSlim);
    }

    /**
     * Resolves skin from a ResolvableProfile using Minecraft's built-in PlayerSkinRenderCache.
     */
    public static SkinData resolveSkinFromProfile(ResolvableProfile profile) {
        if (profile == null) return null;
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.playerSkinRenderCache() != null) {
                PlayerSkinRenderCache.RenderInfo renderInfo = client.playerSkinRenderCache().getOrDefault(profile);
                if (renderInfo != null && renderInfo.playerSkin() != null) {
                    return getSkinDataFromPlayerSkin(renderInfo.playerSkin());
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Resolves a player's skin from their username using local player, online tab list,
     * or Minecraft's PlayerSkinRenderCache.
     */
    public static SkinData resolveSkinFromName(String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();

        // Standard vanilla defaults
        if (trimmed.equalsIgnoreCase("steve")) {
            return DEFAULT_SKIN_DATA;
        }
        if (trimmed.equalsIgnoreCase("alex")) {
            return ALEX_SKIN_DATA;
        }

        Minecraft client = Minecraft.getInstance();

        // 1. Check local client player
        if (client.player != null && client.player.getGameProfile() != null) {
            String localName = client.player.getGameProfile().name();
            if (localName != null && localName.equalsIgnoreCase(trimmed)) {
                return getSkinDataFromPlayerSkin(client.player.getSkin());
            }
        }

        // 2. Check current multiplayer connection (tab list / online players)
        if (client.getConnection() != null) {
            PlayerInfo info = client.getConnection().getPlayerInfoIgnoreCase(trimmed);
            if (info != null) {
                PlayerSkin skin = info.getSkin();
                if (skin != null) {
                    return getSkinDataFromPlayerSkin(skin);
                }
            }
        }

        // 3. Delegate to Minecraft's built-in PlayerSkinRenderCache (asynchronous Mojang API & session resolution)
        try {
            if (client.playerSkinRenderCache() != null) {
                String key = trimmed.toLowerCase();
                ResolvableProfile profile = UNRESOLVED_PROFILE_CACHE.get(key);
                if (profile == null) {
                    if (UNRESOLVED_PROFILE_CACHE.size() >= MAX_PROFILE_CACHE_SIZE) {
                        UNRESOLVED_PROFILE_CACHE.clear();
                    }
                    profile = ResolvableProfile.createUnresolved(trimmed);
                    UNRESOLVED_PROFILE_CACHE.put(key, profile);
                }
                PlayerSkinRenderCache.RenderInfo renderInfo = client.playerSkinRenderCache().getOrDefault(profile);
                if (renderInfo != null && renderInfo.playerSkin() != null) {
                    return getSkinDataFromPlayerSkin(renderInfo.playerSkin());
                }
            }
        } catch (Exception ignored) {}

        return DEFAULT_SKIN_DATA;
    }

    /**
     * Overrides the rendered layer for a Totem of Undying to use our custom 3D player skin model.
     *
     * @param state Render state being assembled for the item stack
     * @param stack The Totem of Undying item stack
     * @param context Item display context (hand, GUI, entity, frame, etc.)
     * @param owner Entity or block context owning the item
     */
    public static void overrideTotemLayer(
        ItemStackRenderState state,
        ItemStack stack,
        ItemDisplayContext context,
        ItemOwner owner
    ) {
        if (state.isEmpty()) return;

        try {
            ItemStackRenderState.LayerRenderState layer = ((ItemStackRenderStateAccessor) state).invokeFirstLayer();
            if (layer == null) return;

            SkinData skinData = resolveSkin(stack, owner);
            if (skinData == null) {
                skinData = DEFAULT_SKIN_DATA;
            }

            // Crucial for GUI / inventory / container rendering:
            // In Minecraft 26.3, DynamicAtlasAllocator caches rendered GUI item icons by
            // TrackingItemStackRenderState.getModelIdentity(). Appending the resolved SkinData
            // (which is a record containing texture Identifier, isSlim, isRainbow, isUpsideDown)
            // ensures each unique totem appearance receives its own dedicated slot in the GuiItemAtlas.
            state.appendModelIdentityElement(skinData);
            if (skinData.isRainbow()) {
                state.setAnimated();
            }

            layer.setupSpecialModel(PlayerTotemRenderer.INSTANCE, skinData);
        } catch (Exception ignored) {
            // Under no circumstances should custom totem layer resolution crash the game or render pipeline
        }
    }

    private static ItemStack clientLastMainHandRef = null;
    private static ItemStack clientLastHeldMainHand = null;
    private static long clientLastHeldMainHandTime = 0L;
    private static ItemStack clientLastOffHandRef = null;
    private static ItemStack clientLastHeldOffHand = null;
    private static long clientLastHeldOffHandTime = 0L;

    /**
     * Called every tick on LocalPlayer to track what totems were held in either hand.
     * This provides a robust fallback if death protection consumes the totem before
     * the client activation packet is handled.
     */
    public static void updateHeldTotems(LocalPlayer player) {
        if (player == null) return;
        long now = System.currentTimeMillis();

        ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (mainHand != null && !mainHand.isEmpty() && isTotem(mainHand)) {
            if (mainHand != clientLastMainHandRef || clientLastHeldMainHand == null
                    || !ItemStack.isSameItemSameComponents(mainHand, clientLastHeldMainHand)) {
                clientLastMainHandRef = mainHand;
                clientLastHeldMainHand = mainHand.copyWithCount(1);
            }
            clientLastHeldMainHandTime = now;
        }

        ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (offHand != null && !offHand.isEmpty() && isTotem(offHand)) {
            if (offHand != clientLastOffHandRef || clientLastHeldOffHand == null
                    || !ItemStack.isSameItemSameComponents(offHand, clientLastHeldOffHand)) {
                clientLastOffHandRef = offHand;
                clientLastHeldOffHand = offHand.copyWithCount(1);
            }
            clientLastHeldOffHandTime = now;
        }
    }

    private static boolean isTotem(ItemStack stack) {
        return stack.is(Items.TOTEM_OF_UNDYING)
            || stack.has(DataComponents.DEATH_PROTECTION);
    }

    /**
     * Intercepts LocalPlayer.displayItemActivation to ensure the activation animation
     * receives the actual popped totem with its player name and profile intact,
     * rather than a blank generic fallback totem.
     */
    public static ItemStack getPopActivationTotem(ItemStack stack, LocalPlayer player) {
        try {
            // 1. If the stack provided already has a profile or custom name, use it
            if (stack != null && !stack.isEmpty()) {
                if (stack.has(DataComponents.PROFILE) || stack.has(DataComponents.CUSTOM_NAME)) {
                    ItemStack copy = stack.copy();
                    copy.setCount(1);
                    return copy;
                }
            }

            // 2. Check if a server-side pop was recorded for this player (singleplayer or modded server)
            ItemStack serverPopped = (player != null && player.getUUID() != null)
                ? TotemPopTracker.getRecordedPoppedTotem(player.getUUID())
                : null;
            if (serverPopped != null && !serverPopped.isEmpty()) {
                if (serverPopped.has(DataComponents.PROFILE) || serverPopped.has(DataComponents.CUSTOM_NAME)) {
                    return serverPopped;
                }
            }

            // 3. Check client-side tracked held totems (for vanilla dedicated servers)
            // Priority matches Minecraft's checkTotemDeathProtection: MAIN_HAND, then OFF_HAND
            long now = System.currentTimeMillis();
            long maxAge = 5000L; // 5 seconds window

            if (clientLastHeldMainHand != null && (now - clientLastHeldMainHandTime < maxAge)) {
                if (clientLastHeldMainHand.has(DataComponents.PROFILE) || clientLastHeldMainHand.has(DataComponents.CUSTOM_NAME)) {
                    return clientLastHeldMainHand.copy();
                }
            }

            if (clientLastHeldOffHand != null && (now - clientLastHeldOffHandTime < maxAge)) {
                if (clientLastHeldOffHand.has(DataComponents.PROFILE) || clientLastHeldOffHand.has(DataComponents.CUSTOM_NAME)) {
                    return clientLastHeldOffHand.copy();
                }
            }

            // 4. If a recorded or tracked totem exists even without custom name, return it (ensures count 1)
            if (serverPopped != null && !serverPopped.isEmpty()) {
                return serverPopped;
            }
            if (clientLastHeldMainHand != null && (now - clientLastHeldMainHandTime < maxAge)) {
                return clientLastHeldMainHand.copy();
            }
            if (clientLastHeldOffHand != null && (now - clientLastHeldOffHandTime < maxAge)) {
                return clientLastHeldOffHand.copy();
            }

            if (stack != null && !stack.isEmpty()) {
                return stack.copyWithCount(1);
            }
        } catch (Exception ignored) {}

        return stack != null ? stack : new ItemStack(Items.TOTEM_OF_UNDYING);
    }

    /**
     * Resolves the skin texture and arm model (Classic vs Slim) for a given Totem of Undying.
     *
     * <h3>Resolution Order & Item Frame vs Anvil Semantics:</h3>
     * <ol>
     *   <li><b>Explicit Profile or Anvil-Renamed Totems (Permanent Player Binding):</b>
     *       When a totem has a {@code PROFILE} or is renamed to a valid Minecraft username in an anvil
     *       (e.g. "Technoblade", "Alex"), it gains a {@code CUSTOM_NAME} component.
     *       These totems are <i>permanently bound</i> to that named player in ALL display contexts:
     *       held in any player's hand, during totem pop activation animations, dropped as an item entity
     *       on the floor, in GUI inventories, or placed on wall item frames.</li>
     *   <li><b>Unrenamed Item Frame Totems (Display Placer Binding):</b>
     *       When an unrenamed totem is placed into an {@link ItemFrame}, {@code ItemFrameEntityMixin}
     *       records the placer's profile onto the placed stack. Wall trophy displays honor whoever hung them up.
     *       If an unrenamed totem is taken out of an item frame, it resumes dynamic context-aware rendering.</li>
     *   <li><b>Living Entity Hands (Contextual Dynamic Binding):</b>
     *       An unrenamed totem held by a player (first-person or third-person) dynamically adopts
     *       the holding player's own skin and model. When handed to someone else, it shifts to theirs.</li>
     *   <li><b>Dropped Items & GUI / Inventory:</b>
     *       Dropped totem items on the ground and totems in inventory slots render the client viewer's
     *       own skin for an immersive, personalized gameplay experience.</li>
     * </ol>
     *
     * @param stack The Totem of Undying item stack
     * @param owner Entity or decoration context currently owning the item stack
     * @return Resolved {@link SkinData}
     */
    public static SkinData resolveSkin(ItemStack stack, ItemOwner owner) {
        try {
            Minecraft client = Minecraft.getInstance();

            // Extract modifiers and target player name from Custom Name or Player Name
            TotemModifier modifier = TotemModifier.EMPTY;
            if (stack != null && stack.has(DataComponents.CUSTOM_NAME)) {
                modifier = TotemModifier.parse(stack.getHoverName().getString());
            }

            // If no custom name was present, check if holding/owner player has an easter egg name (e.g. Dinnerbone)
            if (modifier.isEmpty() && owner != null) {
                AbstractClientPlayer p = null;
                if (owner instanceof AbstractClientPlayer ap) p = ap;
                else if (owner.asLivingEntity() instanceof AbstractClientPlayer ap) p = ap;
                if (p != null && p.getGameProfile() != null && p.getGameProfile().name() != null) {
                    modifier = TotemModifier.fromHolderUsername(p.getGameProfile().name());
                }
            }

            SkinData baseSkin = null;

            // 0. Explicit Profile component or player target from modifier
            ResolvableProfile profile = stack != null ? stack.get(DataComponents.PROFILE) : null;
            if (modifier.playerName() != null) {
                // If stack already carries a matching ResolvableProfile, check online/local first, then reuse profile
                baseSkin = resolveSkinFromName(modifier.playerName());
            }

            // 1. Explicit Profile component (if not already resolved from modifier)
            if (baseSkin == null && profile != null) {
                String profileName = profile.name().orElse(null);
                if (profileName == null && profile.partialProfile() != null) {
                    profileName = profile.partialProfile().name();
                }
                if (profileName != null && !profileName.isBlank()) {
                    // Check local player / online tab list first before hitting async skin cache
                    Minecraft mc = Minecraft.getInstance();
                    String trimmed = profileName.trim();
                    if (mc.player != null && mc.player.getGameProfile() != null
                            && trimmed.equalsIgnoreCase(mc.player.getGameProfile().name())) {
                        baseSkin = getSkinDataFromPlayerSkin(mc.player.getSkin());
                    } else if (mc.getConnection() != null) {
                        PlayerInfo info = mc.getConnection().getPlayerInfoIgnoreCase(trimmed);
                        if (info != null && info.getSkin() != null) {
                            baseSkin = getSkinDataFromPlayerSkin(info.getSkin());
                        }
                    }
                }
                if (baseSkin == null) {
                    baseSkin = resolveSkinFromProfile(profile);
                }
            }

            // 2. In an ItemFrame: show placer's skin if recorded, else viewer's
            if (baseSkin == null && owner instanceof ItemFrame) {
                if (client.player != null) {
                    baseSkin = getSkinDataFromPlayerSkin(client.player.getSkin());
                } else {
                    baseSkin = DEFAULT_SKIN_DATA;
                }
            }

            // 3. Held by another player or yourself: show that holding player's skin and model
            if (baseSkin == null && owner != null) {
                AbstractClientPlayer player = null;
                if (owner instanceof AbstractClientPlayer ap) {
                    player = ap;
                } else if (owner.asLivingEntity() instanceof AbstractClientPlayer ap) {
                    player = ap;
                }
                if (player != null) {
                    baseSkin = getSkinDataFromPlayerSkin(player.getSkin());
                }
            }

            // 4. GUI / Inventory / Container / Ground / Viewer fallback
            if (baseSkin == null) {
                if (client.player != null) {
                    baseSkin = getSkinDataFromPlayerSkin(client.player.getSkin());
                } else {
                    baseSkin = DEFAULT_SKIN_DATA;
                }
            }

            return baseSkin.withModifiers(modifier.isRainbow(), modifier.isUpsideDown());
        } catch (Exception ignored) {}

        return DEFAULT_SKIN_DATA;
    }

    /**
     * Extracts target player username if this totem has been bound via Anvil renaming or Profile component.
     *
     * @param stack Item stack to inspect
     * @return Valid target username (3-16 chars), or {@code null} if unrenamed
     */
    public static String getTotemTargetUsername(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        // 1. Check Custom Name component (e.g. renamed in Anvil with possible tags like DjElectrobox#jeb_)
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            TotemModifier mod = TotemModifier.parse(stack.getHoverName().getString());
            if (mod.playerName() != null) {
                return mod.playerName();
            }
        }

        // 2. Check Profile component
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (profile != null) {
            String name = profile.name().orElse(null);
            if (name == null && profile.partialProfile() != null) {
                name = profile.partialProfile().name();
            }
            if (name != null) {
                name = name.trim();
                TotemModifier mod = TotemModifier.parse(name);
                if (mod.playerName() != null) {
                    return mod.playerName();
                }
            }
        }

        return null;
    }
}
