package com.projecthero.mod.client.miraculous.pegasus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.miraculous.pegasus.entity.HorseshoeEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** v0.16.6: the thrown Horseshoe (and the Volley's small ones), lying flat along its flight and spinning fast. */
public class HorseshoeRenderer extends EntityRenderer<HorseshoeEntity> {
	public HorseshoeRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(HorseshoeEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		Vec3 v = e.getDeltaMovement();
		float heading = v.horizontalDistanceSqr() < 1.0e-6 ? 0f : (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
		pose.mulPose(Axis.YP.rotationDegrees(heading));
		pose.mulPose(Axis.XP.rotationDegrees(90f)); // flat
		pose.mulPose(Axis.ZP.rotationDegrees((e.tickCount + partial) * (e.small() ? 50f : 38f)));
		// (the model is in pixels: 1 = a hand-held horseshoe about 0.45 blocks across)
		float s = e.small() ? 0.9f : 1.6f;
		pose.scale(s, s, s);
		PegasusRender.renderShoeModel(pose, buffers, Math.max(light, LightTexture.pack(10, 10)));
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(HorseshoeEntity e) {
		return PegasusRender.SHOE;
	}
}
