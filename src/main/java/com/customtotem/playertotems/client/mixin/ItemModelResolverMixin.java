package com.customtotem.playertotems.client.mixin;

import com.customtotem.playertotems.client.PlayerTotemClientHandler;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into ItemModelResolver.appendItemLayers to dynamically apply
 * the custom player-skin totem model to any Totem of Undying being rendered.
 */
@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {
    @Inject(method = "appendItemLayers", at = @At("RETURN"))
    private void onAppendItemLayers(
        ItemStackRenderState state,
        ItemStack stack,
        ItemDisplayContext context,
        Level level,
        ItemOwner owner,
        int seed,
        CallbackInfo ci
    ) {
        if (stack.is(Items.TOTEM_OF_UNDYING)) {
            PlayerTotemClientHandler.overrideTotemLayer(state, stack, context, owner);
        }
    }
}
