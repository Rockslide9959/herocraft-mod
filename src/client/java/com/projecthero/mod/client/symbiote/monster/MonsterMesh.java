package com.projecthero.mod.client.symbiote.monster;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Symbiote Monster form: procedural living geometry for the cocoon and the melt (the same technique as the suit's
 * {@code GooMesh}: quads pushed straight into an {@code entityCutoutNoCull} buffer, rebuilt every frame so they can writhe).
 */
final class MonsterMesh {
	private MonsterMesh() {
	}

	static void quad(VertexConsumer vc, PoseStack.Pose pose, int light, int rgb, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float v0, float u1,
			float v1) {
		Vec3 n = b.subtract(a).cross(d.subtract(a));
		double len = n.length();
		float nx = len < 1.0e-6 ? 0f : (float) (n.x / len);
		float ny = len < 1.0e-6 ? 1f : (float) (n.y / len);
		float nz = len < 1.0e-6 ? 0f : (float) (n.z / len);
		vertex(vc, pose, light, a, u0, v0, nx, ny, nz, rgb);
		vertex(vc, pose, light, b, u1, v0, nx, ny, nz, rgb);
		vertex(vc, pose, light, c, u1, v1, nx, ny, nz, rgb);
		vertex(vc, pose, light, d, u0, v1, nx, ny, nz, rgb);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int light, Vec3 p, float u, float v, float nx, float ny, float nz, int rgb) {
		vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
				.setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	/** A tube through {@code points} with per-point radii (frame carried along the curve, so it neither twists nor pinches). */
	static void tube(VertexConsumer vc, PoseStack.Pose pose, int light, int rgb, Vec3[] points, float[] radii, int sides, float uvTwist) {
		int n = points.length;
		if (n < 2) {
			return;
		}
		Vec3[][] rings = new Vec3[n][sides];
		Vec3 prevNormal = null;
		for (int i = 0; i < n; i++) {
			Vec3 t = (i < n - 1 ? points[i + 1].subtract(points[i]) : points[i].subtract(points[i - 1]));
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
				float u0 = ((float) k / sides + uvTwist * v0) % 1.0f;
				float u1 = u0 + 1.0f / sides;
				quad(vc, pose, light, rgb, rings[i][k], rings[i][k2], rings[i + 1][k2], rings[i + 1][k], u0, v0, u1, v1);
			}
		}
	}
}
