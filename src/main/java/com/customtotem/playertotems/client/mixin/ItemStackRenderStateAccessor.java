package com.customtotem.playertotems.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor invoker interface providing access to package-private {@code firstLayer()}
 * on {@link ItemStackRenderState} for special model layer injection.
 */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
    @Invoker("firstLayer")
    ItemStackRenderState.LayerRenderState invokeFirstLayer();
}
