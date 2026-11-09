package com.customtotem.playertotems.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When a Totem of Undying is renamed in an Anvil to a player's username,
 * attach the ResolvableProfile component so the totem is permanently bound to that player's skin.
 * If the name is cleared or not a valid Minecraft player username, remove the profile component.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @Shadow private String itemName;

    @Inject(method = "createResult", at = @At("RETURN"))
    private void onAnvilCreateResult(CallbackInfo ci) {
        try {
            AnvilMenu menu = (AnvilMenu) (Object) this;
            Slot resultSlot = menu.getSlot(AnvilMenu.RESULT_SLOT);
            if (resultSlot != null) {
                ItemStack result = resultSlot.getItem();
                if (result != null && !result.isEmpty() && result.is(Items.TOTEM_OF_UNDYING)) {
                    if (this.itemName != null && !this.itemName.isBlank()) {
                        String name = this.itemName.trim();
                        // Minecraft usernames are 3 to 16 characters (alphanumeric and underscore)
                        if (name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                            result.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(name));
                        } else {
                            result.remove(DataComponents.PROFILE);
                        }
                    } else {
                        result.remove(DataComponents.PROFILE);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
