package com.projecthero.mod.client.miraculous.ladybug;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.16.6: Ladybug's hand-built 3D bits, all real geometry (no particles): the Yo-yo (two spotted halves on a black
 * axle; it spins), the Spinning Shield disc (with blurred copies), the compact (the Yo-yo opened on its hinge, the screen
 * lit), the parachute canopy, the string (a camera-facing ribbon along a sagging curve) and its coils, and the red
 * light burst. Units are blocks; textures in {@code textures/entity/miraculous/ladybug/}.
 */
public final class LadybugGeometry {
	public static final ResourceLocation YOYO = ProjectHeroMod.id("textures/entity/miraculous/ladybug/yoyo.png");
	public static final ResourceLocation SPOTS = ProjectHeroMod.id("textures/entity/miraculous/ladybug/spots.png");
	public static final ResourceLocation BURST = ProjectHeroMod.id("textures/entity/miraculous/ladybug/burst.png");
	/** Yo-yo radius / half thickness / the gap between the halves. */
	public static final float R = 0.11f;
	private static final float HALF = 0.045f;
	private static final float GAP = 0.012f;
	private static final int SEG = 16;
	/** The string's half width. */
	private static final float STRING_W = 0.014f;

	private LadybugGeometry() {
	}

	// ---------------------------------------------------------------- vertices

	private static void v(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float vv, int light, int argb, float nx,
			float ny, float nz) {
		vc.addVertex(p.pose(), x, y, z).setColor(argb).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}

	/** Face tile ({@code tile} 0 = spotted face, 1 = band, 2 = screen) u for a 48x16 texture. */
	private static float tu(int tile, float f) {
		return (tile * 16 + f * 16f) / 48f;
	}

	// ---------------------------------------------------------------- the Yo-yo

	/**
	 * The Yo-yo at the pose's origin, its axle along local X, spun {@code spin} radians about it. {@code open} 0..1 swings
	 * the outer (+X) half open on a hinge at its rim (the compact); the screen inside lights up.
	 */
	public static void yoyo(PoseStack pose, MultiBufferSource buffers, int light, float spin, float open, float scale) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(YOYO));
		pose.pushPose();
		pose.scale(scale, scale, scale);
		pose.mulPose(com.mojang.math.Axis.XP.rotation(spin));
		// the axle
		half(vc, pose.last(), light, -GAP, GAP, 0.025f, 1, 0xFF181416, false);
		// the inner (-X) half
		half(vc, pose.last(), light, -GAP - 2f * HALF, -GAP, R, 0, 0xFFFFFFFF, true);
		// the outer (+X) half, on its hinge at the top of the rim
		pose.pushPose();
		if (open > 0f) {
			pose.translate(GAP, R, 0f);
			pose.mulPose(com.mojang.math.Axis.ZP.rotation(-open * 1.9f));
			pose.translate(-GAP, -R, 0f);
		}
		half(vc, pose.last(), light, GAP, GAP + 2f * HALF, R, 0, 0xFFFFFFFF, true);
		pose.popPose();
		if (open > 0.05f) {
			// the screen on the inner half's face, full bright
			VertexConsumer sc = buffers.getBuffer(RenderType.entityTranslucentEmissive(YOYO));
			int a = Math.round(255 * Math.min(1f, open * 1.5f));
			disc(sc, pose.last(), LightTexture.FULL_BRIGHT, -GAP + 0.002f, R * 0.92f, 2, (a << 24) | 0xFFFFFF, true);
		}
		pose.popPose();
	}

	/** One half: a short cylinder from x0 to x1, its faces spotted (tile 0) or plain. */
	private static void half(VertexConsumer vc, PoseStack.Pose p, int light, float x0, float x1, float r, int faceTile, int argb, boolean faces) {
		for (int i = 0; i < SEG; i++) {
			double a0 = Math.PI * 2 * i / SEG, a1 = Math.PI * 2 * (i + 1) / SEG;
			float y0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r, y1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
			float ny = (float) Math.cos((a0 + a1) / 2), nz = (float) Math.sin((a0 + a1) / 2);
			float ua = tu(1, i / (float) SEG), ub = tu(1, (i + 1) / (float) SEG);
			v(vc, p, x0, y0, z0, ua, 0f, light, argb, 0, ny, nz);
			v(vc, p, x1, y0, z0, ua, 1f, light, argb, 0, ny, nz);
			v(vc, p, x1, y1, z1, ub, 1f, light, argb, 0, ny, nz);
			v(vc, p, x0, y1, z1, ub, 0f, light, argb, 0, ny, nz);
		}
		if (faces) {
			disc(vc, p, light, x0, r, faceTile, argb, false);
			disc(vc, p, light, x1, r, faceTile, argb, true);
		}
	}

	/** A flat disc in the YZ plane at {@code x}, textured with one 16x16 tile mapped round. */
	private static void disc(VertexConsumer vc, PoseStack.Pose p, int light, float x, float r, int tile, int argb, boolean plus) {
		float nx = plus ? 1f : -1f;
		float cu = tu(tile, 0.5f), cv = 0.5f;
		for (int i = 0; i < SEG; i++) {
			double a0 = Math.PI * 2 * i / SEG, a1 = Math.PI * 2 * (i + 1) / SEG;
			float y0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r, y1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
			float u0 = tu(tile, 0.5f + 0.5f * (float) Math.sin(a0)), v0 = 0.5f - 0.5f * (float) Math.cos(a0);
			float u1 = tu(tile, 0.5f + 0.5f * (float) Math.sin(a1)), v1 = 0.5f - 0.5f * (float) Math.cos(a1);
			v(vc, p, x, 0f, 0f, cu, cv, light, argb, nx, 0, 0);
			v(vc, p, x, y0, z0, u0, v0, light, argb, nx, 0, 0);
			v(vc, p, x, y1, z1, u1, v1, light, argb, nx, 0, 0);
			v(vc, p, x, 0f, 0f, cu, cv, light, argb, nx, 0, 0);
		}
	}

	/** The Spinning Shield: a spotted disc of {@code radius} facing local +X, spun, with three fainter blurred copies. */
	public static void shield(PoseStack pose, MultiBufferSource buffers, int light, float spin, float radius) {
		VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(YOYO));
		pose.pushPose();
		pose.mulPose(com.mojang.math.Axis.XP.rotation(spin));
		disc(solid, pose.last(), light, 0f, radius, 0, 0xFFFFFFFF, true);
		disc(solid, pose.last(), light, -0.01f, radius, 0, 0xFFFFFFFF, false);
		pose.popPose();
		VertexConsumer blur = buffers.getBuffer(RenderType.entityTranslucent(YOYO));
		for (int k = 1; k <= 3; k++) {
			pose.pushPose();
			pose.mulPose(com.mojang.math.Axis.XP.rotation(spin - k * 0.35f));
			int a = 120 - k * 30;
			disc(blur, pose.last(), light, 0.006f * k, radius * (1f + 0.04f * k), 0, (a << 24) | 0xFFFFFF, true);
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- the canopy

	/** The parachute canopy: a spotted dome of {@code radius} with its cords down to two points (camera-relative). */
	public static void canopy(PoseStack pose, MultiBufferSource buffers, int light, Vec3 top, float radius, float sway, Vec3 left, Vec3 right,
			Vec3 cam) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SPOTS));
		PoseStack.Pose p = pose.last();
		int rings = 5, seg = 18;
		float height = radius * 0.55f;
		Vec3 c = top.subtract(cam);
		for (int r = 0; r < rings; r++) {
			float t0 = r / (float) rings, t1 = (r + 1) / (float) rings;
			for (int i = 0; i < seg; i++) {
				double a0 = Math.PI * 2 * i / seg + sway, a1 = Math.PI * 2 * (i + 1) / seg + sway;
				float[] q00 = dome(c, radius, height, t0, a0), q01 = dome(c, radius, height, t0, a1);
				float[] q10 = dome(c, radius, height, t1, a0), q11 = dome(c, radius, height, t1, a1);
				float u0 = i / (float) seg * 3f, u1 = (i + 1) / (float) seg * 3f;
				v(vc, p, q00[0], q00[1], q00[2], u0, t0 * 1.5f, light, 0xFFFFFFFF, 0, 1, 0);
				v(vc, p, q01[0], q01[1], q01[2], u1, t0 * 1.5f, light, 0xFFFFFFFF, 0, 1, 0);
				v(vc, p, q11[0], q11[1], q11[2], u1, t1 * 1.5f, light, 0xFFFFFFFF, 0, 1, 0);
				v(vc, p, q10[0], q10[1], q10[2], u0, t1 * 1.5f, light, 0xFFFFFFFF, 0, 1, 0);
			}
		}
		// cords from the rim to her hands
		for (int i = 0; i < 6; i++) {
			double a = Math.PI * 2 * i / 6 + sway;
			float[] rim = dome(c, radius, height, 1f, a);
			Vec3 from = new Vec3(rim[0], rim[1], rim[2]).add(cam);
			string(pose, buffers, light, from, i < 3 ? left : right, cam, 0f, 0f, 0xFFE8E4E4);
		}
	}

	/** A point on the dome: {@code t} 0 = the crown .. 1 = the rim. */
	private static float[] dome(Vec3 c, float radius, float height, float t, double a) {
		double rr = radius * Math.sin(t * Math.PI / 2);
		double y = height * Math.cos(t * Math.PI / 2);
		return new float[] { (float) (c.x + Math.cos(a) * rr), (float) (c.y + y), (float) (c.z + Math.sin(a) * rr) };
	}

	// ---------------------------------------------------------------- the string

	/**
	 * The string from {@code a} to {@code b} (world), sagging by {@code sag} blocks at its middle (0 = taut) and swaying
	 * sideways by {@code sway}: a camera-facing ribbon, {@code argb} tinted.
	 */
	public static void string(PoseStack pose, MultiBufferSource buffers, int light, Vec3 a, Vec3 b, Vec3 cam, float sag, float sway, int argb) {
		double len = a.distanceTo(b);
		if (len < 0.02) {
			return;
		}
		int n = sag > 0.01f || sway != 0f ? Math.max(8, Math.min(24, (int) (len * 2))) : 1;
		Vec3 dir = b.subtract(a).normalize();
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3[] pts = new Vec3[n + 1];
		for (int i = 0; i <= n; i++) {
			double t = i / (double) n;
			double bow = 4.0 * t * (1.0 - t);
			pts[i] = a.lerp(b, t).add(0, -sag * bow, 0).add(side.scale(sway * bow));
		}
		polyline(pose, buffers, light, List.of(pts), cam, argb);
	}

	/** A ribbon through world points. */
	public static void polyline(PoseStack pose, MultiBufferSource buffers, int light, List<Vec3> pts, Vec3 cam, int argb) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(YOYO));
		PoseStack.Pose p = pose.last();
		// a texel inside the screen tile: plain near-white, tinted by argb
		float u = (32f + 2.5f) / 48f, vv = 7.5f / 16f;
		for (int i = 0; i + 1 < pts.size(); i++) {
			Vec3 a = pts.get(i).subtract(cam), b = pts.get(i + 1).subtract(cam);
			Vec3 d = b.subtract(a);
			Vec3 mid = a.add(b).scale(0.5);
			Vec3 w = d.cross(mid);
			if (w.lengthSqr() < 1.0E-9) {
				continue;
			}
			// thinner close to the camera (the first-person hand), so it never reads as a ribbon
			w = w.normalize().scale(STRING_W * Math.max(0.25, Math.min(1.0, mid.length() / 3.0)));
			v(vc, p, (float) (a.x - w.x), (float) (a.y - w.y), (float) (a.z - w.z), u, vv, light, argb, 0, 1, 0);
			v(vc, p, (float) (a.x + w.x), (float) (a.y + w.y), (float) (a.z + w.z), u, vv, light, argb, 0, 1, 0);
			v(vc, p, (float) (b.x + w.x), (float) (b.y + w.y), (float) (b.z + w.z), u, vv, light, argb, 0, 1, 0);
			v(vc, p, (float) (b.x - w.x), (float) (b.y - w.y), (float) (b.z - w.z), u, vv, light, argb, 0, 1, 0);
		}
	}

	/** The string's sag for a line of {@code length} spanning {@code chord} (a shallow catenary). */
	public static float sagFor(double chord, double length) {
		if (length <= chord) {
			return 0f;
		}
		return (float) Math.sqrt(3.0 * chord * (length - chord) / 8.0);
	}

	/**
	 * The bind: {@code turns} coils of string round a body (feet at {@code base}, {@code width} wide, {@code height}
	 * tall), wound on from the top as {@code wound} goes 0..1. Returns the coil's first point (where the line arrives).
	 */
	public static Vec3 coils(PoseStack pose, MultiBufferSource buffers, int light, Vec3 base, double width, double height, float wound, Vec3 cam,
			float phase) {
		int turns = 4, steps = 64;
		double r = width * 0.55 + 0.06;
		double y0 = height * 0.82, y1 = height * 0.22;
		int shown = Math.max(2, Math.round(steps * Mth.clamp(wound, 0f, 1f)));
		java.util.ArrayList<Vec3> pts = new java.util.ArrayList<>();
		for (int i = 0; i <= shown; i++) {
			double t = i / (double) steps;
			double a = t * turns * Math.PI * 2 + phase;
			double y = Mth.lerp(t, y0, y1) + 0.03 * Math.sin(a * 3);
			pts.add(base.add(Math.cos(a) * r, y, Math.sin(a) * r));
		}
		polyline(pose, buffers, light, pts, cam, 0xFFF0EAEA);
		return pts.get(0);
	}

	// ---------------------------------------------------------------- the light burst

	/** A camera-facing burst of ladybug-red light at {@code at}, {@code size} across, {@code alpha} 0..1. */
	public static void burst(PoseStack pose, MultiBufferSource buffers, Vec3 at, Vec3 cam, org.joml.Vector3f camLeft, org.joml.Vector3f camUp,
			float size, float alpha, float spin) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(BURST));
		PoseStack.Pose p = pose.last();
		Vec3 c = at.subtract(cam);
		float cs = Mth.cos(spin) * size, sn = Mth.sin(spin) * size;
		Vec3 l = new Vec3(camLeft.x(), camLeft.y(), camLeft.z()), u = new Vec3(camUp.x(), camUp.y(), camUp.z());
		Vec3 ax = l.scale(cs).add(u.scale(sn)), ay = u.scale(cs).subtract(l.scale(sn));
		int argb = (Math.round(255 * Mth.clamp(alpha, 0f, 1f)) << 24) | 0xFFFFFF;
		int fb = LightTexture.FULL_BRIGHT;
		Matrix4f m = p.pose();
		Vec3[] q = { c.subtract(ax).subtract(ay), c.add(ax).subtract(ay), c.add(ax).add(ay), c.subtract(ax).add(ay) };
		float[][] uv = { { 0, 1 }, { 1, 1 }, { 1, 0 }, { 0, 0 } };
		for (int i = 0; i < 4; i++) {
			vc.addVertex(m, (float) q[i].x, (float) q[i].y, (float) q[i].z).setColor(argb).setUv(uv[i][0], uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(fb).setNormal(p, 0, 1, 0);
		}
	}
}
