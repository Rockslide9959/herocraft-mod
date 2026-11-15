package com.projecthero.mod.client.powerarmour;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.ironman.IronManBoxes;
import com.projecthero.mod.powerarmour.entity.SentryTurretEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** v0.17.5 Power Armour: the Sentry Turret -- a pixel tripod, a body and a head that turns to its target, a red sight light. */
public class SentryTurretRenderer extends EntityRenderer<SentryTurretEntity> {
	public SentryTurretRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.35f;
	}

	@Override
	public void render(SentryTurretEntity t, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(SuitRenderer.MODULE_TEX));
		int o = OverlayTexture.NO_OVERLAY;
		pose.pushPose();
		// tripod (y up, pixels)
		for (int i = 0; i < 3; i++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(i * 120f + 30f));
			pose.mulPose(Axis.XP.rotationDegrees(28f));
			IronManBoxes.draw(pose, vc, IronManBoxes.Box.swatch(-0.5f, 0f, -0.5f, 0.5f, 9f, 0.5f, 1f, 1f, 2f, 2f), 32, 32, light, o, 0xFFFFFFFF);
			pose.popPose();
		}
		IronManBoxes.draw(pose, vc, IronManBoxes.Box.swatch(-2f, 7.5f, -2f, 2f, 10f, 2f, 5f, 1f, 2f, 2f), 32, 32, light, o, 0xFFFFFFFF);
		// head
		Entity target = t.target() >= 0 ? t.level().getEntity(t.target()) : null;
		float headYaw = target != null ? SentryTurretEntity.yawTo(t, target) : t.getYRot() + (t.tickCount + partial) * 2f;
		pose.translate(0, 11.5f / 16f, 0);
		pose.mulPose(Axis.YP.rotationDegrees(-headYaw));
		IronManBoxes.draw(pose, vc, IronManBoxes.Box.swatch(-3f, -2f, -3f, 3f, 2f, 3f, 5f, 1f, 2f, 2f), 32, 32, light, o, 0xFFFFFFFF);
		IronManBoxes.draw(pose, vc, IronManBoxes.Box.swatch(-3.2f, -0.4f, -3.2f, 3.2f, 0.4f, 3.2f, 21f, 1f, 2f, 2f), 32, 32, light, o, 0xFFFFFFFF);
		IronManBoxes.draw(pose, vc, IronManBoxes.Box.swatch(-0.6f, -0.6f, 3f, 0.6f, 0.6f, 9f, 13f, 1f, 2f, 2f), 32, 32, light, o, 0xFFFFFFFF);
		IronManBoxes.draw(pose, buffers.getBuffer(RenderType.eyes(SuitRenderer.MODULE_TEX)),
				IronManBoxes.Box.swatch(1.2f, 0.6f, 3f, 2.4f, 1.6f, 3.3f, 1f, 5f, 2f, 2f), 32, 32, LightTexture.FULL_BRIGHT, o, 0xFFFF4040);
		pose.popPose();
		super.render(t, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(SentryTurretEntity t) {
		return SuitRenderer.MODULE_TEX;
	}
}
