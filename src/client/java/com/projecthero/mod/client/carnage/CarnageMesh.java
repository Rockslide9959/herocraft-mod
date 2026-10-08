package com.projecthero.mod.client.carnage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15: procedural-mesh helpers for Carnage's crimson geometry (whip tendrils, the axe arm, spikes, shards, goo
 * globs, snare coils): quads pushed straight into an {@code entityCutoutNoCull} buffer, plus flat glowing strips for
 * the ground cracks (an additive position-colour buffer).
 */
final class CarnageMesh {
	static final int WHITE = 0xFFFFFFFF;

	private CarnageMesh() {
	}

	static void quad(VertexConsumer vc, PoseStack.Pose pose, int light, int argb, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
			float u0, float v0, float u1, float v1) {
		Vec3 n = b.subtract(a).cross(d.subtract(a));
		double len = n.length();
		float nx = len < 1.0e-6 ? 0f : (float) (n.x / len);
		float ny = len < 1.0e-6 ? 1f : (float) (n.y / len);
		float nz = len < 1.0e-6 ? 0f : (float) (n.z / len);
		vertex(vc, pose, light, argb, a, u0, v0, nx, ny, nz);
		vertex(vc, pose, light, argb, b, u1, v0, nx, ny, nz);
		vertex(vc, pose, light, argb, c, u1, v1, nx, ny, nz);
		vertex(vc, pose, light, argb, d, u0, v1, nx, ny, nz);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int light, int argb, Vec3 p, float u, float v,
			float nx, float ny, float nz) {
		vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
				.setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	/** A tube through {@code points} with per-point {@code radii}, framed by parallel transport (no twisting). */
	static void tube(VertexConsumer vc, PoseStack.Pose pose, int light, int argb, Vec3[] points, float[] radii, int sides) {
		int n = points.length;
		if (n < 2) {
			return;
		}
		Vec3[][] rings = new Vec3[n][sides];
		Vec3 prevNormal = null;
		for (int i = 0; i < n; i++) {
			Vec3 t = i < n - 1 ? points[i + 1].subtract(points[i]) : points[i].subtract(points[i - 1]);
			if (t.lengthSqr() < 1.0e-10) {
				t = new Vec3(0, 1, 0);
			}
			t = t.normalize();
			Vec3 normal;
			if (prevNormal == null) {
				Vec3 ref = Math.abs(t.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
				normal = t.cross(ref).normalize();
			} else {
				normal = prevNormal.subtract(t.scale(prevNormal.dot(t)));
				normal = normal.lengthSqr() < 1.0e-8 ? t.cross(new Vec3(0, 1, 0)).normalize() : normal.normalize();
			}
			prevNormal = normal;
			Vec3 binormal = t.cross(normal);
			for (int k = 0; k < sides; k++) {
				double ang = Math.PI * 2 * k / sides;
				rings[i][k] = points[i].add(normal.scale(Math.cos(ang) * radii[i])).add(binormal.scale(Math.sin(ang) * radii[i]));
			}
		}
		for (int i = 0; i < n - 1; i++) {
			float v0 = (float) i / (n - 1);
			float v1 = (float) (i + 1) / (n - 1);
			for (int k = 0; k < sides; k++) {
				int k2 = (k + 1) % sides;
				float u0 = (float) k / sides;
				quad(vc, pose, light, argb, rings[i][k], rings[i][k2], rings[i + 1][k2], rings[i + 1][k], u0, v0, u0 + 1.0f / sides, v1);
			}
		}
	}

	/** A straight cone from {@code base} (radius {@code r}) to {@code tip}, slightly bulged so it reads as organic. */
	static void cone(VertexConsumer vc, PoseStack.Pose pose, int light, int argb, Vec3 base, Vec3 tip, float r, int sides) {
		Vec3 axis = tip.subtract(base);
		Vec3[] pts = new Vec3[5];
		float[] radii = new float[5];
		for (int i = 0; i < 5; i++) {
			double t = i / 4.0;
			pts[i] = base.add(axis.scale(t));
			radii[i] = (float) (r * (1.0 - t) * (1.0 + 0.25 * Math.sin(t * Math.PI))) + (i == 4 ? 0.004f : 0f);
		}
		tube(vc, pose, light, argb, pts, radii, sides);
	}

	/** A lumpy, wobbling blob of goo. */
	static void blob(VertexConsumer vc, PoseStack.Pose pose, int light, int argb, Vec3 c, float r, float age, int seed) {
		int lat = 7, lon = 10;
		Vec3[][] p = new Vec3[lat + 1][lon + 1];
		for (int i = 0; i <= lat; i++) {
			double th = Math.PI * i / lat;
			for (int j = 0; j <= lon; j++) {
				double ph = Math.PI * 2 * j / lon;
				double wob = 1.0 + 0.14 * Math.sin(3 * ph + age * 0.5 + seed) * Math.sin(2 * th + age * 0.3);
				double rr = r * wob;
				p[i][j] = c.add(Math.sin(th) * Math.cos(ph) * rr, Math.cos(th) * rr, Math.sin(th) * Math.sin(ph) * rr);
			}
		}
		for (int i = 0; i < lat; i++) {
			for (int j = 0; j < lon; j++) {
				quad(vc, pose, light, argb, p[i][j], p[i][j + 1], p[i + 1][j + 1], p[i + 1][j],
						(float) j / lon, (float) i / lat, (float) (j + 1) / lon, (float) (i + 1) / lat);
			}
		}
	}

	/** A flat glowing strip from {@code a} to {@code b} on the horizontal plane at their height (both faces). */
	static void glowStrip(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, float wa, float wb, int r, int g, int bl, int alpha) {
		Vec3 d = b.subtract(a);
		Vec3 side = new Vec3(-d.z, 0, d.x);
		if (side.lengthSqr() < 1.0e-8) {
			return;
		}
		side = side.normalize();
		Vec3 p0 = a.add(side.scale(wa)), p1 = b.add(side.scale(wb)), p2 = b.subtract(side.scale(wb)), p3 = a.subtract(side.scale(wa));
		glow(vc, pose, p0, r, g, bl, alpha);
		glow(vc, pose, p1, r, g, bl, alpha);
		glow(vc, pose, p2, r, g, bl, alpha);
		glow(vc, pose, p3, r, g, bl, alpha);
		glow(vc, pose, p3, r, g, bl, alpha);
		glow(vc, pose, p2, r, g, bl, alpha);
		glow(vc, pose, p1, r, g, bl, alpha);
		glow(vc, pose, p0, r, g, bl, alpha);
	}

	private static void glow(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, int r, int g, int b, int a) {
		vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
	}
}
