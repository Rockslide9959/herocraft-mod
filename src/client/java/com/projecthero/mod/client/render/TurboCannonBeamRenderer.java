package com.projecthero.mod.client.render;

import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.maxsteel.entity.TurboCannonBeamEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.2: the Turbo Cannon discharge. A thick column of blue-white T.U.R.B.O. energy from the muzzle to where the
 * shot stopped: a white core, a blue sheath and a wide faint halo, with glowing rings travelling down it and a
 * flash sphere at each end. It flares to full width over the first two ticks, then narrows and fades over the rest of
 * the entity's short life. Wider and hotter the more the cannon was charged.
 */
public class TurboCannonBeamRenderer extends EntityRenderer<TurboCannonBeamEntity> {
	public TurboCannonBeamRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public boolean shouldRender(TurboCannonBeamEntity entity, Frustum frustum, double x, double y, double z) {
		return true; // the entity sits at the muzzle, but the beam can reach 48 blocks off-screen of it
	}

	@Override
	public void render(TurboCannonBeamEntity beam, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers,
			int light) {
		Vec3 start = beam.position();
		Vec3 delta = beam.end().subtract(start);
		double len = delta.length();
		if (len < 0.1) {
			return;
		}
		float age = beam.tickCount + partialTicks;
		float life = TurboCannonBeamEntity.LIFE_TICKS;
		float flare = Math.min(1f, age / 2f);
		float fade = Math.max(0f, 1f - Math.max(0f, age - 2f) / (life - 2f));
		float charge = beam.charge();
		float w = beam.width() * flare * (0.35f + 0.65f * fade);
		float alpha = fade;
		int sheath = TurboDraw.toWhite(TurboDraw.BLUE, 0.15f * charge);

		float ry = (float) (Mth.atan2(delta.x, delta.z) * Mth.RAD_TO_DEG);
		float rx = (float) (Mth.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * Mth.RAD_TO_DEG);
		VertexConsumer vc = TurboDraw.buffer(buffers);

		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(ry));
		pose.mulPose(Axis.XP.rotationDegrees(-rx));

		// the column (boxes centred at half the length)
		pose.pushPose();
		pose.translate(0f, 0f, (float) (len / 2));
		pose.mulPose(Axis.ZP.rotationDegrees(age * 20f));
		float half = (float) (len / 2);
		TurboDraw.box(vc, pose.last(), w * 0.28f, w * 0.28f, half, 0xFFFFFF, alpha);
		TurboDraw.box(vc, pose.last(), w * 0.55f, w * 0.55f, half, sheath, 0.55f * alpha);
		pose.mulPose(Axis.ZP.rotationDegrees(45f));
		TurboDraw.box(vc, pose.last(), w * 0.6f, w * 0.6f, half, sheath, 0.35f * alpha);
		TurboDraw.box(vc, pose.last(), w, w, half, sheath, 0.12f * alpha);
		pose.popPose();

		// rings racing down the beam
		int rings = Math.max(2, (int) (len / 3.0));
		for (int k = 0; k < rings; k++) {
			float z = (float) (((k / (float) rings) + age * 0.09f) % 1.0f * len);
			pose.pushPose();
			pose.translate(0f, 0f, z);
			pose.mulPose(Axis.ZP.rotationDegrees(age * 35f + k * 20f));
			float r = w * 0.95f;
			for (int s = 0; s < 4; s++) {
				pose.pushPose();
				pose.mulPose(Axis.ZP.rotationDegrees(s * 90f));
				pose.translate(r, 0f, 0f);
				TurboDraw.box(vc, pose.last(), w * 0.07f, r * 0.72f, w * 0.07f, sheath, 0.8f * alpha);
				pose.popPose();
			}
			pose.popPose();
		}

		// flash at the muzzle and at the impact
		TurboDraw.orb(vc, pose, w * 0.45f, sheath, age * 20f, alpha);
		pose.translate(0f, 0f, (float) len);
		TurboDraw.orb(vc, pose, w * (0.5f + 0.25f * charge) * (0.6f + 0.4f * flare), sheath, age * -25f, alpha);
		pose.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(TurboCannonBeamEntity entity) {
		return TurboDraw.WHITE;
	}
}
