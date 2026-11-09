package com.customtotem.playertotems.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * Custom SpecialModelRenderer that dynamically renders the totem geometry
 * textured with the player's skin.
 */
public class PlayerTotemRenderer implements SpecialModelRenderer<PlayerTotemClientHandler.SkinData> {
    public static final PlayerTotemRenderer INSTANCE = new PlayerTotemRenderer();
    private static final Identifier DEFAULT_SKIN = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

    @Override
    public void submit(PlayerTotemClientHandler.SkinData skinData, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean hasFoil, int color) {
        if (skinData == null || skinData.texture() == null) {
            skinData = new PlayerTotemClientHandler.SkinData(DEFAULT_SKIN, false);
        }

        Identifier skinTexture = skinData.texture();
        if (skinTexture == null) {
            skinTexture = DEFAULT_SKIN;
        } else {
            String path = skinTexture.getPath();
            // Defensive sanitization: ensure path is a valid texture resource
            if (!path.startsWith("textures/") && !path.startsWith("skins/")) {
                skinTexture = skinTexture.withPath("textures/" + path + ".png");
            }
        }
        boolean isSlim = skinData.isSlim();

        // entityCutout discards fragments with alpha < 0.1 and avoids depth sorting issues
        RenderType renderType = RenderTypes.entityCutout(skinTexture, false);

        poseStack.pushPose();
        // Scale and align 0..16 Blockbench coordinate space with Minecraft's item box
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        poseStack.translate(-8.0f, -8.0f, -8.0f);

        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            TotemModelGeometry.render(pose, consumer, light, overlay, isSlim);
        });

        if (hasFoil) {
            collector.submitCustomGeometry(poseStack, RenderTypes.itemCutoutGlint(skinTexture), (pose, consumer) -> {
                TotemModelGeometry.render(pose, consumer, light, overlay, isSlim);
            });
        }

        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer) {
        consumer.accept(new Vector3f(0.0f, 0.0f, 0.0f));
        consumer.accept(new Vector3f(1.0f, 1.0f, 1.0f));
    }

    @Override
    public PlayerTotemClientHandler.SkinData extractArgument(ItemStack stack) {
        return PlayerTotemClientHandler.resolveSkin(stack, null);
    }
}
