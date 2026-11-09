package com.customtotem.playertotems.mixin;

import com.customtotem.playertotems.TotemPopTracker;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into LivingEntity.checkTotemDeathProtection to capture the exact totem stack
 * (including its CUSTOM_NAME and PROFILE components) immediately before vanilla shrinks it.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"))
    private void onCheckTotemDeathProtection(DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Player player)) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack inHand = player.getItemInHand(hand);
            if (inHand != null && !inHand.isEmpty() && inHand.has(DataComponents.DEATH_PROTECTION)) {
                TotemPopTracker.recordPoppedTotem(player.getUUID(), inHand);
                break;
            }
        }
    }
}
