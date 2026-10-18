package com.projecthero.mod.client.miraculous.pegasus;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.miraculous.pegasus.entity.PegasusPortalEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.16.6: a Pegasus portal -- a golden-white hexagonal ring (a band with depth and a bright core stripe) round a
 * swirling, shimmering surface: two layers of the swirl turning opposite ways and breathing, drawn full-bright from both
 * sides. It grows out of a point of light as it opens and shrinks away as it closes ({@link PegasusPortalEntity#openness}).
 * (Not a see-through window: a real view of the far side would need a second world render; the swirl stands in for it.)
 */
public class PegasusPortalRenderer extends EntityRenderer<PegasusPortalEntity> {
	public static final ResourceLocation SWIRL = ProjectHeroMod.id("textures/entity/miraculous/pegasus/portal_swirl.png");
	public static final ResourceLocation RING = ProjectHeroMod.id("textures/entity/miraculous/pegasus/portal_ring.png");

	public PegasusPortalRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(PegasusPortalEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float open = e.openness(partial);
		if (open <= 0.01f) {
			return;
		}
		float time = e.age() + partial;
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-e.faceYaw()));
		pose.mulPose(Axis.XP.rotationDegrees(e.facePitch()));
		float r = e.radius() * (0.15f + 0.85f * ease(open));
		draw(pose, buffers, r, time, open);
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	/** The portal in the XY plane, facing +Z, {@code r} from the centre to a corner. Also used by the waypoint markers. */
	public static void draw(PoseStack pose, MultiBufferSource buffers, float r, float time, float alpha) {
		Matrix4f m = pose.last().pose();
		int full = LightTexture.FULL_BRIGHT;
		// the surface: two swirl layers turning opposite ways
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(SWIRL));
		float inner = r * 0.84f;
		float breathe = 1f + 0.04f * Mth.sin(time * 0.25f);
		surface(vc, pose, m, inner * breathe, time * 0.045f, 0f, (int) (215 * alpha), full);
		surface(vc, pose, m, inner * 0.92f, -time * 0.07f + 1.1f, 0.012f, (int) (150 * alpha), full);
		surface(vc, pose, m, inner * 0.92f, -time * 0.07f + 1.1f, -0.012f, (int) (150 * alpha), full);
		// the ring
		VertexConsumer rc = buffers.getBuffer(RenderType.entityTranslucentEmissive(RING));
		float depth = Math.max(0.05f, r * 0.06f);
		int a = (int) (255 * alpha);
		float shimmer = time * 0.06f;
		for (int k = 0; k < 6; k++) {
			double a0 = Math.PI / 2 + k * Math.PI / 3, a1 = a0 + Math.PI / 3;
			float ox0 = (float) Math.cos(a0) * r, oy0 = (float) Math.sin(a0) * r, ox1 = (float) Math.cos(a1) * r, oy1 = (float) Math.sin(a1) * r;
			float ix0 = (float) Math.cos(a0) * inner, iy0 = (float) Math.sin(a0) * inner, ix1 = (float) Math.cos(a1) * inner, iy1 = (float) Math.sin(a1) * inner;
			float u0 = k / 6f + shimmer, u1 = (k + 1) / 6f + shimmer;
			// front and back faces of the band (v across it: 0 outside .. 1 inside)
			for (float z : new float[] { depth, -depth }) {
				quad(rc, pose, m, ox0, oy0, z, u0, 0f, ox1, oy1, z, u1, 0f, ix1, iy1, z, u1, 1f, ix0, iy0, z, u0, 1f, a, full, z > 0 ? 1f : -1f);
			}
			// the outer and inner rims
			quad(rc, pose, m, ox0, oy0, depth, u0, 0.3f, ox1, oy1, depth, u1, 0.3f, ox1, oy1, -depth, u1, 0.7f, ox0, oy0, -depth, u0, 0.7f, a, full, 0f);
			quad(rc, pose, m, ix0, iy0, -depth, u0, 0.3f, ix1, iy1, -depth, u1, 0.3f, ix1, iy1, depth, u1, 0.7f, ix0, iy0, depth, u0, 0.7f, a, full, 0f);
		}
	}

	/** A hexagon of the swirl, its texture turned by {@code turn} radians, at {@code z}. */
	private static void surface(VertexConsumer vc, PoseStack pose, Matrix4f m, float r, float turn, float z, int alpha, int light) {
		float c = Mth.cos(turn), s = Mth.sin(turn);
		for (int k = 0; k < 6; k++) {
			double a0 = Math.PI / 2 + k * Math.PI / 3, a1 = a0 + Math.PI / 3;
			float x0 = (float) Math.cos(a0) * r, y0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, y1 = (float) Math.sin(a1) * r;
			float[] uv0 = uv(x0 / r, y0 / r, c, s), uv1 = uv(x1 / r, y1 / r, c, s), uvc = uv(0f, 0f, c, s);
			quad(vc, pose, m, 0f, 0f, z, uvc[0], uvc[1], x0, y0, z, uv0[0], uv0[1], x1, y1, z, uv1[0], uv1[1], x1, y1, z, uv1[0], uv1[1], alpha, light, 1f);
		}
	}

	private static float[] uv(float x, float y, float c, float s) {
		return new float[] { 0.5f + 0.5f * (x * c - y * s), 0.5f + 0.5f * (x * s + y * c) };
	}

	private static void quad(VertexConsumer vc, PoseStack pose, Matrix4f m, float x0, float y0, float z0, float u0, float v0, float x1, float y1, float z1, float u1,
			float v1, float x2, float y2, float z2, float u2, float v2, float x3, float y3, float z3, float u3, float v3, int alpha, int light, float nz) {
		vtx(vc, pose, m, x0, y0, z0, u0, v0, alpha, light, nz);
		vtx(vc, pose, m, x1, y1, z1, u1, v1, alpha, light, nz);
		vtx(vc, pose, m, x2, y2, z2, u2, v2, alpha, light, nz);
		vtx(vc, pose, m, x3, y3, z3, u3, v3, alpha, light, nz);
	}

	private static void vtx(VertexConsumer vc, PoseStack pose, Matrix4f m, float x, float y, float z, float u, float v, int alpha, int light, float nz) {
		vc.addVertex(m, x, y, z).setColor(255, 255, 255, Mth.clamp(alpha, 0, 255)).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose.last(), 0f, nz == 0f ? 1f : 0f, nz);
	}

	private static float ease(float t) {
		return t * t * (3f - 2f * t);
	}

	@Override
	public boolean shouldRender(PegasusPortalEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
		return frustum.isVisible(e.getBoundingBoxForCulling());
	}

	@Override
	public ResourceLocation getTextureLocation(PegasusPortalEntity e) {
		return SWIRL;
	}
}
