package com.customtotem.playertotems.client.mixin;

import com.customtotem.playertotems.client.PlayerTotemClientHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into LocalPlayer to:
 * 1. Track held totems continuously so client knows what was in hand right before a pop.
 * 2. Intercept displayItemActivation to supply the original renamed/profiled totem stack
 *    instead of a blank fallback totem.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void onLocalPlayerTick(CallbackInfo ci) {
        PlayerTotemClientHandler.updateHeldTotems((LocalPlayer) (Object) this);
    }

    @ModifyVariable(method = "displayItemActivation", at = @At("HEAD"), argsOnly = true)
    private ItemStack onDisplayItemActivation(ItemStack stack) {
        return PlayerTotemClientHandler.getPopActivationTotem(stack, (LocalPlayer) (Object) this);
    }
}
