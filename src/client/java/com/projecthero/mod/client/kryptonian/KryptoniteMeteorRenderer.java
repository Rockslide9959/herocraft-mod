package com.projecthero.mod.client.kryptonian;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.kryptonian.item.KryptonianItems;
import com.projecthero.mod.kryptonian.meteor.KryptoniteMeteorEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * v0.14.8: the Kryptonite Meteor in flight -- a tumbling 2x2 lump of glowing kryptonite ore drawn at full brightness
 * (the vanilla on-fire overlay wraps it in flames; the smoke and green trail are server particles).
 */
public class KryptoniteMeteorRenderer extends EntityRenderer<KryptoniteMeteorEntity> {
	public KryptoniteMeteorRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public void render(KryptoniteMeteorEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		float t = entity.tickCount + partialTick;
		pose.translate(0.0, 1.0, 0.0);
		pose.mulPose(Axis.YP.rotationDegrees(t * 9.0f));
		pose.mulPose(Axis.XP.rotationDegrees(t * 6.0f));
		pose.scale(2.0f, 2.0f, 2.0f);
		pose.translate(-0.5, -0.5, -0.5);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(KryptonianItems.KRYPTONITE_ORE.defaultBlockState(), pose, buffers,
				0xF000F0, OverlayTexture.NO_OVERLAY);
		pose.popPose();
		super.render(entity, yaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(KryptoniteMeteorEntity entity) {
		return InventoryMenu.BLOCK_ATLAS;
	}
}
