package com.projecthero.mod.client.mutation.wind;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.16.5 Wind Manipulation: the shared procedural geometry every wind model is built from -- streaked translucent
 * bands (a ring / funnel slice), flat rings, ribbons (gust streak lines) and spheres. Everything is drawn into an
 * {@code entityTranslucent} buffer of {@link #STREAK} (wispy white streaks over a faint base) so the air reads as moving
 * air, and the u coordinate scrolls with time for the spin.
 */
public final class WindMesh {
	/** Tileable wispy streaks (scratchpad/gen_wind_v0165.js). */
	public static final ResourceLocation STREAK = ProjectHeroMod.id("textures/entity/mutation/p24_streak.png");
	/** Plain white, for the solid gust streak lines and the blade's body. */
	public static final ResourceLocation SOLID = com.projecthero.mod.client.mutation.MutationRender.WHITE;
	/** A pale sky-blue white, so the air still reads against the sky. */
	public static final int R = 214;
	public static final int G = 234;
	public static final int B = 252;

	private WindMesh() {
	}

	public static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int r, int g,
			int b, int a, int light, float nx, float ny, float nz) {
		vc.addVertex(pose, x, y, z).setColor(r, g, b, Mth.clamp(a, 0, 255)).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(pose, nx, ny, nz);
	}

	public static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int a, int light) {
		vertex(vc, pose, x, y, z, u, v, R, G, B, a, light, 0, 1, 0);
	}

	/**
	 * A vertical band of air round the y axis, from {@code y0} (radius {@code r0}) to {@code y1} (radius {@code r1}) --
	 * one slice of a funnel or a wall. {@code scroll} turns the streaks; {@code a0} / {@code a1} are the alphas at the
	 * bottom and top edge.
	 */
	public static void band(VertexConsumer vc, PoseStack.Pose pose, float y0, float r0, float y1, float r1, int segs, float uTiles,
			float scroll, float v0, float v1, int a0, int a1, int light) {
		for (int s = 0; s < segs; s++) {
			float t0 = Mth.TWO_PI * s / segs;
			float t1 = Mth.TWO_PI * (s + 1) / segs;
			float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
			float u0 = (float) s / segs * uTiles + scroll;
			float u1 = (float) (s + 1) / segs * uTiles + scroll;
			vertex(vc, pose, c0 * r0, y0, s0 * r0, u0, v0, R, G, B, a0, light, c0, 0, s0);
			vertex(vc, pose, c1 * r0, y0, s1 * r0, u1, v0, R, G, B, a0, light, c1, 0, s1);
			vertex(vc, pose, c1 * r1, y1, s1 * r1, u1, v1, R, G, B, a1, light, c1, 0, s1);
			vertex(vc, pose, c0 * r1, y1, s0 * r1, u0, v1, R, G, B, a1, light, c0, 0, s0);
		}
	}

	/** A flat ring in the xz plane between radii {@code rIn} and {@code rOut} at height {@code y}. */
	public static void flatRing(VertexConsumer vc, PoseStack.Pose pose, float y, float rIn, float rOut, int segs, float scroll,
			int aIn, int aOut, int light) {
		for (int s = 0; s < segs; s++) {
			float t0 = Mth.TWO_PI * s / segs;
			float t1 = Mth.TWO_PI * (s + 1) / segs;
			float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
			float u0 = (float) s / segs * 3 + scroll;
			float u1 = (float) (s + 1) / segs * 3 + scroll;
			vertex(vc, pose, c0 * rIn, y, s0 * rIn, u0, 0.1f, aIn, light);
			vertex(vc, pose, c1 * rIn, y, s1 * rIn, u1, 0.1f, aIn, light);
			vertex(vc, pose, c1 * rOut, y, s1 * rOut, u1, 0.9f, aOut, light);
			vertex(vc, pose, c0 * rOut, y, s0 * rOut, u0, 0.9f, aOut, light);
		}
	}

	/**
	 * A gust streak line: a thin ribbon from {@code a} to {@code b}, {@code w} wide, turned to face {@code toEye} as well
	 * as it can. Fades in at {@code a} and out at {@code b}.
	 */
	public static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vector3f a, Vector3f b, float w, Vector3f toEye, int alpha,
			float u0, float u1, int light) {
		Vector3f d = new Vector3f(b).sub(a);
		if (d.lengthSquared() < 1.0e-6f) {
			return;
		}
		Vector3f side = new Vector3f(d).cross(toEye);
		if (side.lengthSquared() < 1.0e-6f) {
			side.set(0, 1, 0).cross(d);
		}
		side.normalize(w * 0.5f);
		int aMid = alpha;
		int aEnd = alpha / 5;
		vertex(vc, pose, a.x - side.x, a.y - side.y, a.z - side.z, u0, 0.2f, aEnd, light);
		vertex(vc, pose, a.x + side.x, a.y + side.y, a.z + side.z, u0, 0.8f, aEnd, light);
		Vector3f m = new Vector3f(a).add(b).mul(0.5f);
		vertex(vc, pose, m.x + side.x, m.y + side.y, m.z + side.z, (u0 + u1) * 0.5f, 0.8f, aMid, light);
		vertex(vc, pose, m.x - side.x, m.y - side.y, m.z - side.z, (u0 + u1) * 0.5f, 0.2f, aMid, light);
		vertex(vc, pose, m.x - side.x, m.y - side.y, m.z - side.z, (u0 + u1) * 0.5f, 0.2f, aMid, light);
		vertex(vc, pose, m.x + side.x, m.y + side.y, m.z + side.z, (u0 + u1) * 0.5f, 0.8f, aMid, light);
		vertex(vc, pose, b.x + side.x, b.y + side.y, b.z + side.z, u1, 0.8f, aEnd, light);
		vertex(vc, pose, b.x - side.x, b.y - side.y, b.z - side.z, u1, 0.2f, aEnd, light);
	}

	/** A sphere of radius {@code r} round the origin, brighter at its rim, streaks scrolling round it. */
	public static void sphere(VertexConsumer vc, PoseStack.Pose pose, float r, int rings, int segs, float scroll, int r8, int g8, int b8,
			int aPole, int aRim, int light) {
		for (int i = 0; i < rings; i++) {
			float p0 = Mth.PI * i / rings - Mth.HALF_PI;
			float p1 = Mth.PI * (i + 1) / rings - Mth.HALF_PI;
			float y0 = Mth.sin(p0) * r, y1 = Mth.sin(p1) * r;
			float q0 = Mth.cos(p0) * r, q1 = Mth.cos(p1) * r;
			int a0 = (int) Mth.lerp(Mth.cos(p0), aPole, aRim);
			int a1 = (int) Mth.lerp(Mth.cos(p1), aPole, aRim);
			for (int s = 0; s < segs; s++) {
				float t0 = Mth.TWO_PI * s / segs;
				float t1 = Mth.TWO_PI * (s + 1) / segs;
				float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
				float u0 = (float) s / segs * 2 + scroll;
				float u1 = (float) (s + 1) / segs * 2 + scroll;
				float v0 = (float) i / rings, v1 = (float) (i + 1) / rings;
				vertex(vc, pose, c0 * q0, y0, s0 * q0, u0, v0, r8, g8, b8, a0, light, c0, y0 / r, s0);
				vertex(vc, pose, c1 * q0, y0, s1 * q0, u1, v0, r8, g8, b8, a0, light, c1, y0 / r, s1);
				vertex(vc, pose, c1 * q1, y1, s1 * q1, u1, v1, r8, g8, b8, a1, light, c1, y1 / r, s1);
				vertex(vc, pose, c0 * q1, y1, s0 * q1, u0, v1, r8, g8, b8, a1, light, c0, y1 / r, s0);
			}
		}
	}

	/**
	 * A spiral arm in the xy plane (z = 0): {@code turns} of a curve winding in from {@code rOut} to {@code rIn},
	 * starting at angle {@code a0}, {@code w} wide.
	 */
	public static void spiralArm(VertexConsumer vc, PoseStack.Pose pose, float rOut, float rIn, float a0, float turns, float w, int steps,
			int alphaOut, int alphaIn, float scroll, int light) {
		for (int i = 0; i < steps; i++) {
			float f0 = (float) i / steps, f1 = (float) (i + 1) / steps;
			float r0 = Mth.lerp(f0, rOut, rIn), r1 = Mth.lerp(f1, rOut, rIn);
			float t0 = a0 + f0 * turns * Mth.TWO_PI, t1 = a0 + f1 * turns * Mth.TWO_PI;
			float w0 = w * (1 - f0 * 0.6f), w1 = w * (1 - f1 * 0.6f);
			float x0 = Mth.cos(t0), y0 = Mth.sin(t0), x1 = Mth.cos(t1), y1 = Mth.sin(t1);
			int a0i = (int) Mth.lerp(f0, alphaOut, alphaIn), a1i = (int) Mth.lerp(f1, alphaOut, alphaIn);
			float u0 = f0 * 2 + scroll, u1 = f1 * 2 + scroll;
			vertex(vc, pose, x0 * (r0 - w0), y0 * (r0 - w0), 0, u0, 0.2f, R, G, B, a0i, light, 0, 0, 1);
			vertex(vc, pose, x1 * (r1 - w1), y1 * (r1 - w1), 0, u1, 0.2f, R, G, B, a1i, light, 0, 0, 1);
			vertex(vc, pose, x1 * (r1 + w1), y1 * (r1 + w1), 0, u1, 0.8f, R, G, B, a1i, light, 0, 0, 1);
			vertex(vc, pose, x0 * (r0 + w0), y0 * (r0 + w0), 0, u0, 0.8f, R, G, B, a0i, light, 0, 0, 1);
		}
	}
}
