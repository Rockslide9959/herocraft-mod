package com.projecthero.mod.client.omnitrix;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.1: the loose Omnitrix item is the watch's own 3D model ({@link OmnitrixModel}) wherever it is drawn -- in the hand,
 * in the inventory, on the ground, in a frame. Its item model is {@code builtin/entity}; the display transforms in
 * {@code models/item/omnitrix.json} pose it (three-quarter from above in the GUI).
 */
public final class OmnitrixItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	@Override
	public void render(ItemStack stack, ItemDisplayContext mode, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		pose.pushPose();
		pose.translate(0.5f, 0.5f, 0.5f);
		VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(OmnitrixWatch.TEX), false, stack.hasFoil());
		drawCentred(pose, vc, light);
		pose.popPose();
	}

	/** The watch, face up (+y), centred on the origin (the open cuff runs along x). */
	public static void drawCentred(PoseStack pose, VertexConsumer vc, int light) {
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(90f));
		pose.translate(-1.5f / 16f, -OmnitrixModel.CY / 16f, 0f);
		OmnitrixModel.draw(pose, vc, light, 3f, 0f, 0f, false);
		pose.popPose();
	}
}
