package com.projecthero.mod.client.miraculous.carapace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.miraculous.carapace.entity.ShieldThrowEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** v0.16.6: the thrown Shield, lying flat (face up) with a slight tilt and spinning fast like a discus. */
public class ShieldThrowRenderer extends EntityRenderer<ShieldThrowEntity> {
	public ShieldThrowRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.shadowRadius = 0.3f;
	}

	@Override
	public void render(ShieldThrowEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		pose.translate(0f, 0.15f, 0f);
		pose.mulPose(Axis.YP.rotationDegrees((e.tickCount + partialTick) * 55f));
		pose.mulPose(Axis.XP.rotationDegrees(-90f + 12f));
		CarapaceRender.renderShieldModel(pose, buffers, light, 1f);
		pose.popPose();
		super.render(e, yaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(ShieldThrowEntity e) {
		return CarapaceRender.SHIELD;
	}
}
