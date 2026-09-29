package com.projecthero.mod.client.darkseid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import com.projecthero.mod.darkseid.entity.MotherBoxEntity;
import com.projecthero.mod.darkseid.item.DarkseidItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A raid Mother Box: the Mother Box item's own 3D model, enlarged, floating and turning. Active boxes are
 * full-bright and bob; one close to overloading shudders; a disrupted box sits dark and still. Its floating name
 * (drawn by the vanilla name-tag path) shows its state and the disruption percentage.
 */
public class MotherBoxRenderer extends EntityRenderer<MotherBoxEntity> {
	private ItemStack stack;

	public MotherBoxRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5f;
	}

	@Override
	public void render(MotherBoxEntity box, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		if (stack == null) {
			stack = new ItemStack(DarkseidItems.MOTHER_BOX);
		}
		float age = box.tickCount + partialTick;
		boolean active = box.isActive();
		// v0.13.19: a disabled box being switched back on rises, shudders, spins up and lights up over the warning
		float waking = active ? 0.0f : box.waking();
		poseStack.pushPose();
		float bob = active ? (float) Math.sin(age * 0.08f) * 0.12f : -0.35f + 0.35f * waking;
		poseStack.translate(0.0f, 0.5f + bob, 0.0f);
		if (waking > 0.0f) {
			float jitter = 0.04f + waking * 0.08f;
			poseStack.translate((Math.random() - 0.5) * jitter, (Math.random() - 0.5) * jitter, (Math.random() - 0.5) * jitter);
			poseStack.mulPose(Axis.YP.rotationDegrees(age * 12.0f * waking));
		}
		float warn = box.overloadWarning();
		if (active && warn > 0.66f) {
			float jitter = (warn - 0.66f) * 0.12f;
			poseStack.translate((Math.random() - 0.5) * jitter, (Math.random() - 0.5) * jitter, (Math.random() - 0.5) * jitter);
		}
		if (active) {
			poseStack.mulPose(Axis.YP.rotationDegrees(age * (2.0f + box.progress() * 10.0f)));
		} else {
			poseStack.mulPose(Axis.ZP.rotationDegrees(12.0f * (1.0f - waking)));
		}
		poseStack.scale(1.8f, 1.8f, 1.8f);
		// waking: flicker between dark and full-bright, flickering faster (and more often lit) as it nears
		boolean lit = active || (waking > 0.0f && Math.sin(age * (0.6f + waking * 1.6f)) > 0.6f - waking * 1.2f);
		Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, lit ? 0xF000F0 : light,
				OverlayTexture.NO_OVERLAY, poseStack, buffers, box.level(), box.getId());
		poseStack.popPose();
		super.render(box, yaw, partialTick, poseStack, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(MotherBoxEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
