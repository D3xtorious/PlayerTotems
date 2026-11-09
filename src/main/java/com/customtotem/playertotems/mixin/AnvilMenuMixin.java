package com.customtotem.playertotems.mixin;

import com.customtotem.playertotems.TotemModifier;
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
 * When a Totem of Undying is renamed in an Anvil to a player's username or easter egg tag,
 * attach the ResolvableProfile component if a specific player name is defined.
 * If the name is cleared or has no valid player name, remove the profile component.
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
                        TotemModifier mod = TotemModifier.parse(this.itemName);
                        if (mod.playerName() != null) {
                            result.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(mod.playerName()));
                        } else {
                            result.remove(DataComponents.PROFILE);
                        }
                    } else {
                        result.remove(DataComponents.PROFILE);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
