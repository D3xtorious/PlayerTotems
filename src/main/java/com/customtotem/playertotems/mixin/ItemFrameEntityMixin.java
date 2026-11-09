package com.customtotem.playertotems.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Attaches the placer's profile to totems placed inside an Item Frame
 * so wall displays can remember who placed them.
 */
@Mixin(ItemFrame.class)
public abstract class ItemFrameEntityMixin {
    @Shadow public abstract ItemStack getItem();

    @Inject(method = "interact", at = @At("HEAD"))
    private void onInteract(Player player, InteractionHand hand, Vec3 pos, CallbackInfoReturnable<InteractionResult> cir) {
        try {
            if (this.getItem().isEmpty()) {
                ItemStack held = player.getItemInHand(hand);
                if (held != null && held.is(Items.TOTEM_OF_UNDYING)) {
                    if (!held.has(DataComponents.PROFILE) && !held.has(DataComponents.CUSTOM_NAME)) {
                        held.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
