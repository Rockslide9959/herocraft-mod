package com.projecthero.mod.client.moonknight;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.moonknight.entity.CrescentDartEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Crescent Dart (R): a procedural silver crescent blade -- a tapered arc about 0.5 blocks across, thick along its
 * inner rim and knife-thin at the outer edge -- spinning flat like a thrown shuriken, drawn full-bright so it gleams
 * like moonlight at night. No model file: the mesh is built here every frame from a handful of arc samples.
 *
 * <p>Oriented to its flight the way vanilla turns an arrow ({@code Axis.YP(yaw - 90)} then {@code Axis.ZP(pitch)}, with
 * yaw / pitch from the velocity, falling back to the synced rotation when it has stopped -- a Moon Mark stuck in a
 * target), then spun about its own local up axis. A stuck dart stops spinning with its thick back half buried.
 * Texture: {@code textures/entity/crescent_dart.png} (original, from {@code scratchpad/gen_mk_phase34_assets.js}).
 */
public class CrescentDartRenderer extends EntityRenderer<CrescentDartEntity> {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/crescent_dart.png");
	private static final int SEGMENTS = 16;
	/** Half the arc's sweep (radians): the tips reach round to about 130 degrees either side of the belly. */
	private static final double SWEEP = 2.3;
	private static final double RADIUS = 0.2;
	private static final double HALF_WIDTH = 0.075;
	private static final double THICK = 0.028;

	public CrescentDartRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public void render(CrescentDartEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int light) {
		Vec3 v = e.getDeltaMovement();
		float yaw;
		float pitch;
		if (!e.isStuck() && v.lengthSqr() > 1.0e-6) {
			yaw = (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
			pitch = (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG);
		} else {
			yaw = Mth.lerp(partialTick, e.yRotO, e.getYRot());
			pitch = Mth.lerp(partialTick, e.xRotO, e.getXRot());
		}
		pose.pushPose();
		pose.translate(0.0, 0.1, 0.0);
		pose.mulPose(Axis.YP.rotationDegrees(yaw - 90.0f));
		pose.mulPose(Axis.ZP.rotationDegrees(pitch));
		if (!e.isStuck()) {
			pose.mulPose(Axis.YP.rotationDegrees((e.tickCount + partialTick) * 47.0f));
		} else {
			pose.translate(0.1, 0.0, 0.0); // half buried, belly first
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		mesh(vc, pose.last(), LightTexture.FULL_BRIGHT);
		pose.popPose();
		super.render(e, entityYaw, partialTick, pose, buffers, light);
	}

	/**
	 * The blade in the local XZ plane, belly at +X, tips curling round toward -X. Cross-section is a wedge: full
	 * thickness at the inner rim, a sharp outer edge. Double-sided render type, so winding doesn't matter.
	 */
	private static void mesh(VertexConsumer vc, PoseStack.Pose pose, int light) {
		Vec3[] innerTop = new Vec3[SEGMENTS + 1];
		Vec3[] innerBottom = new Vec3[SEGMENTS + 1];
		Vec3[] outer = new Vec3[SEGMENTS + 1];
		float[] us = new float[SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			double t = (double) i / SEGMENTS;
			double theta = -SWEEP + 2.0 * SWEEP * t;
			double taper = Math.pow(Math.sin(Math.PI * t), 0.8);
			double hw = HALF_WIDTH * taper + 0.004;
			double th = THICK * taper + 0.004;
			double c = Math.cos(theta);
			double s = Math.sin(theta);
			innerTop[i] = new Vec3(c * (RADIUS - hw), th, s * (RADIUS - hw));
			innerBottom[i] = new Vec3(c * (RADIUS - hw), -th, s * (RADIUS - hw));
			outer[i] = new Vec3(c * (RADIUS + hw), 0.0, s * (RADIUS + hw));
			us[i] = (float) t;
		}
		for (int i = 0; i < SEGMENTS; i++) {
			float u0 = us[i];
			float u1 = us[i + 1];
			// top bevel (inner rim -> sharp edge), bottom bevel, and the inner rim itself
			quad(vc, pose, light, innerTop[i], innerTop[i + 1], outer[i + 1], outer[i], u0, 0.25f, u1, 1.0f);
			quad(vc, pose, light, innerBottom[i], innerBottom[i + 1], outer[i + 1], outer[i], u0, 0.25f, u1, 1.0f);
			quad(vc, pose, light, innerTop[i], innerTop[i + 1], innerBottom[i + 1], innerBottom[i], u0, 0.0f, u1, 0.25f);
		}
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose pose, int light, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
			float u0, float v0, float u1, float v1) {
		Vec3 n = b.subtract(a).cross(d.subtract(a));
		double len = n.length();
		float nx = len < 1.0e-9 ? 0f : (float) (n.x / len);
		float ny = len < 1.0e-9 ? 1f : (float) (n.y / len);
		float nz = len < 1.0e-9 ? 0f : (float) (n.z / len);
		vertex(vc, pose, light, a, u0, v0, nx, ny, nz);
		vertex(vc, pose, light, b, u1, v0, nx, ny, nz);
		vertex(vc, pose, light, c, u1, v1, nx, ny, nz);
		vertex(vc, pose, light, d, u0, v1, nx, ny, nz);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int light, Vec3 p, float u, float v,
			float nx, float ny, float nz) {
		vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
				.setColor(255, 255, 255, 255)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	@Override
	public ResourceLocation getTextureLocation(CrescentDartEntity entity) {
		return TEXTURE;
	}
}
