package com.projecthero.mod.client.gyro;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.gyro.model.SteelBallRender;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.gyro.entity.SteelBallEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Gyro Zeppeli: a thrown Steel Ball, drawn by the shared {@link SteelBallRender} spinning fast about the line it flies (the
 * spiralling trail is particles, {@link GyroFx}). Golden while it carries a Golden Spin or Ball Breaker.
 */
public final class SteelBallRenderer extends EntityRenderer<SteelBallEntity> {
	/** A thrown ball's radius (a touch bigger than in the hand, so it reads in flight). */
	public static final float RADIUS = 0.2f;

	public SteelBallRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(SteelBallEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		pose.translate(0f, e.getBbHeight() * 0.5f, 0f);
		// spin about the direction of flight: tip the ball's Y axis onto the velocity
		Vec3 v = e.getDeltaMovement();
		if (v.lengthSqr() > 1.0e-5) {
			Vec3 d = v.normalize();
			float fy = (float) (Mth.atan2(d.x, d.z) * Mth.RAD_TO_DEG);
			float fp = (float) (Math.acos(Mth.clamp(d.y, -1.0, 1.0)) * Mth.RAD_TO_DEG);
			pose.mulPose(Axis.YP.rotationDegrees(fy));
			pose.mulPose(Axis.XP.rotationDegrees(fp));
		}
		float spin = ((e.tickCount + partial) * 48f) % 360f;
		int lit = e.golden() ? LightTexture.FULL_BRIGHT : Math.max(light, LightTexture.pack(10, 15));
		SteelBallRender.render(pose, buffers, lit, RADIUS, spin, e.golden());
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(SteelBallEntity e) {
		return MutationRender.WHITE;
	}
}
