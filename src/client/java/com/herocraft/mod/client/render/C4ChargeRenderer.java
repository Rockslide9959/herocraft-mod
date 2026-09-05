package com.herocraft.mod.client.render;

import com.herocraft.mod.punisher.entity.C4ChargeEntity;
import com.herocraft.mod.punisher.item.PunisherItems;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** Renders a placed C4 charge as a small dark block model sitting on its surface. */
public class C4ChargeRenderer extends EntityRenderer<C4ChargeEntity> {
	private final ItemRenderer itemRenderer;
	private final ItemStack model;

	public C4ChargeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemRenderer = context.getItemRenderer();
		this.model = new ItemStack(PunisherItems.C4_CHARGE);
		this.shadowRadius = 0.2f;
	}

	@Override
	public void render(C4ChargeEntity entity, float yaw, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int light) {
		pose.pushPose();
		pose.translate(0.0, 0.16, 0.0);
		pose.scale(0.55f, 0.55f, 0.55f);
		pose.mulPose(Axis.YP.rotationDegrees(entity.tickCount * 1.5f % 360f));
		itemRenderer.renderStatic(this.model, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
				pose, buffers, entity.level(), entity.getId());
		pose.popPose();
		super.render(entity, yaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(C4ChargeEntity entity) {
		return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
	}
}
