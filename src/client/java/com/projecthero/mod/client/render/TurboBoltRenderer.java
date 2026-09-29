package com.projecthero.mod.client.render;

import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.maxsteel.entity.TurboBoltEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.2: the Turbo Blast bolt finally has a model. A white-hot lance of T.U.R.B.O. energy: an elongated core inside
 * two cyan sheaths and a faint halo, a spinning ring of four vanes at its tail and a leading collar -- all full-bright
 * and see-through ({@link TurboDraw}). It grows with the charge (synced from the server), and a full-charge bolt
 * turns hotter and adds a second counter-rotating collar.
 *
 * <p>Built along +Z and turned to its velocity the way vanilla turns an arrow: yaw = atan2(dx, dz), pitch =
 * atan2(dy, horizontal).
 */
public class TurboBoltRenderer extends EntityRenderer<TurboBoltEntity> {
	public TurboBoltRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public void render(TurboBoltEntity bolt, float yaw, float partialTicks, PoseStack pose,
			MultiBufferSource buffers, int light) {
		Vec3 v = bolt.getDeltaMovement();
		float ry;
		float rx;
		if (v.lengthSqr() > 1.0e-6) {
			ry = (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
			rx = (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG);
		} else {
			ry = bolt.getYRot();
			rx = bolt.getXRot();
		}
		float charge = bolt.charge();
		boolean full = charge >= 0.98f;
		float size = 1.0f + 0.6f * charge;
		float time = bolt.tickCount + partialTicks;
		int glow = TurboDraw.toWhite(TurboDraw.CYAN, full ? 0.3f : 0.0f);

		pose.pushPose();
		pose.translate(0.0f, bolt.getBbHeight() * 0.5f, 0.0f);
		pose.mulPose(Axis.YP.rotationDegrees(ry));
		pose.mulPose(Axis.XP.rotationDegrees(-rx));
		pose.scale(size, size, size);
		VertexConsumer vc = TurboDraw.buffer(buffers);

		// the lance: core, two sheaths, halo -- stretched along the flight
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(time * 25f));
		TurboDraw.box(vc, pose.last(), 0.035f, 0.035f, 0.4f, 0xFFFFFF, 1.0f);
		TurboDraw.box(vc, pose.last(), 0.06f, 0.06f, 0.46f, glow, 0.6f);
		pose.mulPose(Axis.ZP.rotationDegrees(45f));
		TurboDraw.box(vc, pose.last(), 0.08f, 0.08f, 0.5f, glow, 0.3f);
		TurboDraw.box(vc, pose.last(), 0.12f, 0.12f, 0.56f, glow, 0.1f);
		pose.popPose();

		// the tail vanes, spinning
		pose.pushPose();
		pose.translate(0f, 0f, -0.3f);
		pose.mulPose(Axis.ZP.rotationDegrees(time * -40f));
		for (int k = 0; k < 4; k++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(k * 90f));
			pose.translate(0.1f, 0f, 0f);
			TurboDraw.box(vc, pose.last(), 0.05f, 0.012f, 0.1f, glow, 0.8f);
			pose.popPose();
		}
		pose.popPose();

		// the leading collar (two on a full charge, counter-rotating)
		collar(vc, pose, 0.24f, time * 30f, glow, 0.11f);
		if (full) {
			collar(vc, pose, 0.1f, time * -45f, 0xFFFFFF, 0.15f);
		}
		pose.popPose();
		super.render(bolt, yaw, partialTicks, pose, buffers, light);
	}

	private static void collar(VertexConsumer vc, PoseStack pose, float z, float spin, int rgb, float radius) {
		pose.pushPose();
		pose.translate(0f, 0f, z);
		pose.mulPose(Axis.ZP.rotationDegrees(spin));
		for (int k = 0; k < 4; k++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(k * 90f));
			pose.translate(radius, 0f, 0f);
			TurboDraw.box(vc, pose.last(), 0.02f, radius * 0.75f, 0.02f, rgb, 0.75f);
			pose.popPose();
		}
		pose.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(TurboBoltEntity entity) {
		return TurboDraw.WHITE;
	}
}
