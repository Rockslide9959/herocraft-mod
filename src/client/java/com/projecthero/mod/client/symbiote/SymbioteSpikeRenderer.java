package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.symbiote.entity.SymbioteSpikeEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.19: the Symbiote Spike's model -- a long, hooked black thorn, thick at the shoulder and needle-thin at
 * the point, with three barbs raked back from its tip and a ragged tail, so it can never be mistaken for
 * Tendril Strike's tendril.
 *
 * <p>The mesh is built along +X (tip at +X). It is turned to face its flight the way vanilla turns an arrow:
 * {@code Axis.YP(yaw - 90)} then {@code Axis.ZP(pitch)}, with yaw = atan2(dx, dz) and pitch = atan2(dy, horizontal)
 * of its velocity -- Ry(-90) takes +X to +Z (yaw 0 = travelling south) and a positive Z roll tips +X upward.
 */
public class SymbioteSpikeRenderer extends EntityRenderer<SymbioteSpikeEntity> {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/symbiote_spike.png");
	private static final int SIDES = 5;

	public SymbioteSpikeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public void render(SymbioteSpikeEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int light) {
		Vec3 v = e.getDeltaMovement();
		float yaw;
		float pitch;
		if (v.lengthSqr() > 1.0e-6) {
			yaw = (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
			pitch = (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG);
		} else {
			yaw = Mth.lerp(partialTick, e.yRotO, e.getYRot());
			pitch = Mth.lerp(partialTick, e.xRotO, e.getXRot());
		}
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(yaw - 90.0f));
		pose.mulPose(Axis.ZP.rotationDegrees(pitch));
		// a slow twist along its length, like a thrown blade of living tissue
		pose.mulPose(Axis.XP.rotationDegrees((e.tickCount + partialTick) * 25.0f));

		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		PoseStack.Pose last = pose.last();
		// the thorn: tail -> shoulder -> point, slightly hooked upward at the end
		Vec3[] body = {
				new Vec3(-0.55, 0.0, 0.0), new Vec3(-0.35, 0.0, 0.0), new Vec3(-0.12, 0.0, 0.0),
				new Vec3(0.12, 0.0, 0.0), new Vec3(0.34, 0.015, 0.0), new Vec3(0.52, 0.04, 0.0), new Vec3(0.66, 0.07, 0.0)
		};
		float[] radii = { 0.015f, 0.05f, 0.085f, 0.075f, 0.05f, 0.025f, 0.002f };
		GooMesh.tube(vc, last, light, body, radii, SIDES, 0.0f);
		// three barbs raked back from just behind the point
		for (int k = 0; k < 3; k++) {
			double ang = Math.PI * 2 * k / 3.0;
			Vec3 root = new Vec3(0.22, 0.01, 0.0);
			Vec3 tip = new Vec3(0.02, Math.cos(ang) * 0.14, Math.sin(ang) * 0.14);
			GooMesh.tube(vc, last, light, new Vec3[]{root, tip}, new float[]{0.03f, 0.003f}, 4, 0.0f);
		}
		// a ragged tail: two short strands trailing behind
		for (int k = 0; k < 2; k++) {
			double s = k == 0 ? 1 : -1;
			GooMesh.tube(vc, last, light, new Vec3[]{new Vec3(-0.5, 0, 0), new Vec3(-0.72, 0.04 * s, 0.05 * s)},
					new float[]{0.02f, 0.003f}, 4, 0.0f);
		}
		pose.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(SymbioteSpikeEntity entity) {
		return TEXTURE;
	}
}
