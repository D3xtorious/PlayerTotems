package com.customtotem.playertotems.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
    private static final Identifier WHITE_TEXTURE = Identifier.fromNamespaceAndPath("playertotems", "textures/misc/white.png");
    private static final org.joml.Matrix4fc FLIP_Z_180 = new org.joml.Matrix4f().rotationZ((float) Math.PI);
    private static final Vector3fc EXTENT_MIN = new Vector3f(0.0f, 0.0f, 0.0f);
    private static final Vector3fc EXTENT_MAX = new Vector3f(1.0f, 1.0f, 1.0f);
    private static boolean whiteTextureRegistered = false;

    private static Identifier getGuaranteedWhiteTexture() {
        if (!whiteTextureRegistered) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getTextureManager() != null) {
                try {
                    NativeImage img = new NativeImage(1, 1, false);
                    img.setPixel(0, 0, 0xFFFFFFFF);
                    DynamicTexture tex = new DynamicTexture(() -> "playertotems_white", img);
                    client.getTextureManager().register(WHITE_TEXTURE, tex);
                    whiteTextureRegistered = true;
                } catch (Exception ignored) {
                }
            }
        }
        return WHITE_TEXTURE;
    }

    @Override
    public void submit(PlayerTotemClientHandler.SkinData skinData, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean hasFoil, int color) {
        if (skinData == null) {
            skinData = PlayerTotemClientHandler.DEFAULT_SKIN_DATA;
        }

        Identifier skinTexture = skinData.texture();
        boolean isSlim = skinData.isSlim();

        // entityCutout discards fragments with alpha < 0.1 and avoids depth sorting issues
        RenderType renderType = RenderTypes.entityCutout(skinTexture, false);

        poseStack.pushPose();
        // Scale and align 0..16 Blockbench coordinate space with Minecraft's item box
        poseStack.translate(0.5f, 0.5f, 0.5f);

        // Dinnerbone / Grumm upside-down flip
        if (skinData.isUpsideDown()) {
            poseStack.mulPose(FLIP_Z_180);
        }

        poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        poseStack.translate(-8.0f, -8.0f, -8.0f);

        // 1. If this totem has the jeb_ effect, render the 3D volumetric crystal rainbow aura
        if (skinData.isRainbow()) {
            RenderType borderRenderType = RenderTypes.entityCutout(getGuaranteedWhiteTexture(), false);
            collector.submitCustomGeometry(poseStack, borderRenderType, (pose, consumer) -> {
                TotemModelGeometry.renderRainbowBorder(pose, consumer, light, overlay, isSlim);
            });
        }

        // 2. Render the actual player totem with their authentic, untinted player skin!
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            TotemModelGeometry.render(pose, consumer, light, overlay, isSlim, 0xFFFFFFFF);
        });

        if (hasFoil) {
            collector.submitCustomGeometry(poseStack, RenderTypes.itemCutoutGlint(skinTexture), (pose, consumer) -> {
                TotemModelGeometry.render(pose, consumer, light, overlay, isSlim, 0xFFFFFFFF);
            });
        }

        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer) {
        consumer.accept(EXTENT_MIN);
        consumer.accept(EXTENT_MAX);
    }

    @Override
    public PlayerTotemClientHandler.SkinData extractArgument(ItemStack stack) {
        return PlayerTotemClientHandler.resolveSkin(stack, null);
    }
}
