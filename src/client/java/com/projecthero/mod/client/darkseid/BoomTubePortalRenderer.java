package com.projecthero.mod.client.darkseid;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.darkseid.entity.BoomTubePortalEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: a Boom Tube as the comics and films draw it -- a hole torn in space. A white-hot ring that jitters and crackles,
 * a tunnel of blue-white energy rings falling away behind it into a blinding far end, a swirling surface across the mouth,
 * jagged arcs licking off the rim and the dark "Kirby krackle" dots flickering round it. It bursts open from a point and
 * snaps shut ({@link BoomTubePortalEntity#openness}). {@link #draw} is shared with the Darkseid raid's tubes.
 */
public class BoomTubePortalRenderer extends EntityRenderer<BoomTubePortalEntity> {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/boom_tube.png");

	private static final int WHITE = 0xF4FAFF;
	private static final int CYAN = 0x8FD8FF;
	private static final int DEEP = 0x1C3AA8;
	private static final int KRACKLE = 0x0A0E2A;

	public BoomTubePortalRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(BoomTubePortalEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float open = e.openness(partial);
		if (open <= 0.01f) {
			return;
		}
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-e.faceYaw()));
		pose.mulPose(Axis.XP.rotationDegrees(e.facePitch()));
		float burst = open < 1f ? 1f + (1f - open) * 0.3f : 1f;
		draw(pose, buffers, e.radius() * ease(open) * burst, e.age() + partial, open, e.getId());
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	/** The tube in the XY plane, its mouth facing +Z and its tunnel running back along -Z, {@code r} to the rim. */
	public static void draw(PoseStack pose, MultiBufferSource buffers, float r, float time, float alpha, int seed) {
		if (r <= 0.02f) {
			return;
		}
		VertexConsumer vc = TurboDraw.buffer(buffers);
		// ---- the tunnel: rings of energy falling away into the distance, each turning a little faster
		int rings = 11;
		for (int i = rings; i >= 1; i--) {
			float f = i / (float) rings;
			float z = -f * r * 3.2f;
			float rr = r * (1f - f * 0.62f);
			int col = lerp(CYAN, DEEP, f);
			float a = alpha * (0.75f - f * 0.45f);
			pose.pushPose();
			pose.translate(0f, 0f, z);
			pose.mulPose(Axis.ZP.rotationDegrees(time * (4f + i * 1.5f) + i * 17f));
			ring(vc, pose, rr, 0.05f + 0.03f * (1f - f), 18, col, a, time, i);
			pose.popPose();
		}
		// the blinding far end
		pose.pushPose();
		pose.translate(0f, 0f, -r * 3.25f);
		disc(buffers, pose, r * 0.42f, time * 6f, WHITE, alpha);
		pose.popPose();
		vc = TurboDraw.buffer(buffers); // fetched again: the disc switched render type
		// ---- the mouth: two counter-turning swirls of light
		pose.pushPose();
		pose.translate(0f, 0f, -0.02f);
		disc(buffers, pose, r * 0.98f, time * 5f, CYAN, alpha * 0.55f);
		disc(buffers, pose, r * 0.7f, -time * 9f, WHITE, alpha * 0.45f);
		pose.popPose();
		vc = TurboDraw.buffer(buffers);
		// ---- the rim: a white-hot ring with a cyan corona, jittering
		ring(vc, pose, r, 0.11f, 28, WHITE, alpha, time, 99);
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(-time * 7f));
		ring(vc, pose, r * 1.07f, 0.06f, 28, CYAN, alpha * 0.55f, time, 77);
		pose.popPose();
		// ---- jagged arcs licking off the rim (re-rolled every 3 ticks)
		int roll = (int) (time / 3f);
		for (int k = 0; k < 4; k++) {
			float h = hash(seed * 31 + roll * 7 + k);
			float ang = h * Mth.TWO_PI;
			arc(vc, pose, r, ang, r * (0.4f + 0.5f * hash(seed + roll + k * 13)), roll * 11 + k, alpha);
		}
		// ---- the Kirby krackle: dark dots flickering round the tube
		for (int k = 0; k < 18; k++) {
			float h = hash(seed * 7 + k * 19);
			float ang = h * Mth.TWO_PI + time * 0.02f;
			float dist = r * (1.12f + 0.35f * hash(k * 5 + seed));
			float s = 0.05f + 0.07f * hash(k + roll * 3);
			if (hash(k * 3 + roll) < 0.25f) {
				continue; // flicker
			}
			pose.pushPose();
			pose.translate(Mth.cos(ang) * dist, Mth.sin(ang) * dist, 0.02f);
			TurboDraw.box(vc, pose.last(), s, s, 0.01f, KRACKLE, 0.85f * alpha);
			TurboDraw.box(vc, pose.last(), s * 1.6f, s * 1.6f, 0.008f, CYAN, 0.25f * alpha);
			pose.popPose();
		}
	}

	/** A ring of {@code n} short bars, radius {@code r}, bar thickness {@code t}, its bars jittering outward a touch. */
	private static void ring(VertexConsumer vc, PoseStack pose, float r, float t, int n, int rgb, float alpha, float time, int salt) {
		float seg = Mth.TWO_PI * r / n * 0.55f;
		for (int k = 0; k < n; k++) {
			float ang = k * Mth.TWO_PI / n;
			float jit = 1f + 0.035f * Mth.sin(time * 1.7f + k * 2.3f + salt);
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotation(ang));
			pose.translate(r * jit, 0f, 0f);
			TurboDraw.box(vc, pose.last(), t, seg, t, rgb, alpha);
			TurboDraw.box(vc, pose.last(), t * 2.2f, seg * 1.05f, t * 2.2f, CYAN, 0.18f * alpha);
			pose.popPose();
		}
	}

	/** A forked bolt from the rim at angle {@code ang} running {@code len} outward. */
	private static void arc(VertexConsumer vc, PoseStack pose, float r, float ang, float len, int salt, float alpha) {
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotation(ang));
		float x = r;
		float y = 0f;
		int steps = 5;
		for (int i = 0; i < steps; i++) {
			float nx = x + len / steps;
			float ny = y + (hash(salt * 13 + i) - 0.5f) * len * 0.5f;
			float dx = nx - x;
			float dy = ny - y;
			float l = Mth.sqrt(dx * dx + dy * dy);
			pose.pushPose();
			pose.translate((x + nx) / 2f, (y + ny) / 2f, 0.03f);
			pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
			TurboDraw.box(vc, pose.last(), l / 2f, 0.025f, 0.025f, WHITE, alpha);
			TurboDraw.box(vc, pose.last(), l / 2f, 0.06f, 0.05f, CYAN, 0.3f * alpha);
			pose.popPose();
			x = nx;
			y = ny;
		}
		pose.popPose();
	}

	/** A square of the boom-tube swirl texture, turned {@code turnDeg}, tinted {@code rgb}. */
	private static void disc(MultiBufferSource buffers, PoseStack pose, float half, float turnDeg, int rgb, float alpha) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(turnDeg));
		Matrix4f m = pose.last().pose();
		int cr = (rgb >> 16) & 0xFF;
		int cg = (rgb >> 8) & 0xFF;
		int cb = rgb & 0xFF;
		int a = Mth.clamp((int) (alpha * 255), 0, 255);
		int full = LightTexture.FULL_BRIGHT;
		for (int side = 0; side < 2; side++) {
			float nz = side == 0 ? 1f : -1f;
			float s = side == 0 ? 1f : -1f;
			vc.addVertex(m, -half * s, -half, 0).setColor(cr, cg, cb, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose.last(), 0, 0, nz);
			vc.addVertex(m, half * s, -half, 0).setColor(cr, cg, cb, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose.last(), 0, 0, nz);
			vc.addVertex(m, half * s, half, 0).setColor(cr, cg, cb, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose.last(), 0, 0, nz);
			vc.addVertex(m, -half * s, half, 0).setColor(cr, cg, cb, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(pose.last(), 0, 0, nz);
		}
		pose.popPose();
	}

	private static int lerp(int a, int b, float t) {
		int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return (r << 16) | (g << 8) | bl;
	}

	private static float hash(int n) {
		n = (n << 13) ^ n;
		return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / (float) 0x7fffffff;
	}

	private static float ease(float t) {
		return t * t * (3f - 2f * t);
	}

	@Override
	public boolean shouldRender(BoomTubePortalEntity e, Frustum frustum, double x, double y, double z) {
		return frustum.isVisible(e.getBoundingBoxForCulling());
	}

	@Override
	public ResourceLocation getTextureLocation(BoomTubePortalEntity e) {
		return TEXTURE;
	}
}
