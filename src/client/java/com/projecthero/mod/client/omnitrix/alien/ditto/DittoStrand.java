package com.projecthero.mod.client.omnitrix.alien.ditto;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "make the clones almost stretch and pull themselves out of the player and separate from the player please, make it
 * look good and how it looks like in the show please"): the living strand between Ditto and a copy coming out of him (or going back
 * in) -- one smooth tube of his own skin, thick where it leaves each body and necking in the middle like a cell dividing. When it
 * parts the neck pinches to nothing and the two halves snap back into the bodies.
 */
final class DittoStrand {
	private static final int RINGS = 9, SIDES = 10;
	/** The white of the skin's arm (px of 64): the strand is him, stretched thin. */
	private static final float U0 = 44f / 64f, U1 = 48f / 64f, V0 = 21f / 64f, V1 = 31f / 64f;

	private DittoStrand() {
	}

	/**
	 * @param a      his middle (pose space, blocks)
	 * @param b      the copy's middle
	 * @param radius the strand's radius where it leaves a body
	 * @param neck   its thickness at the middle, 0..1 of {@code radius}
	 * @param snap   0 whole .. 1 parted and snapped back
	 * @param time   ticks, for the ripple running along it
	 */
	static void draw(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float radius, float neck, float snap, float time, int light) {
		if (snap >= 1f || a.distanceToSqr(b) < 1.0e-4) {
			return;
		}
		float dist = (float) a.distanceTo(b);
		// a soft sag: it has weight, and gives way as it thins
		Vec3 mid = a.add(b).scale(0.5).add(0, -0.1 * dist * (1f - neck), 0);
		half(pose, vc, a, mid, b, radius, neck, snap, time, light);
		half(pose, vc, b, mid, a, radius, neck, snap, time + 3f, light);
	}

	/** The half from {@code from} to the middle, on the curve through {@code mid} to {@code to}. */
	private static void half(PoseStack pose, VertexConsumer vc, Vec3 from, Vec3 mid, Vec3 to, float radius, float neck, float snap, float time, int light) {
		PoseStack.Pose last = pose.last();
		float reach = 1f - snap; // how far along its half it still gets as it recoils
		float[][] prev = null;
		for (int j = 0; j <= RINGS; j++) {
			float v = j / (float) RINGS;
			float s = v * reach * 0.5f; // along the whole curve
			Vec3 c = curve(from, mid, to, s);
			Vec3 t = curve(from, mid, to, s + 0.02f).subtract(curve(from, mid, to, Math.max(0f, s - 0.02f)));
			t = t.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : t.normalize();
			Vec3 side = Math.abs(t.y) > 0.95 ? t.cross(new Vec3(1, 0, 0)).normalize() : t.cross(new Vec3(0, 1, 0)).normalize();
			Vec3 up = side.cross(t);
			// wide at the body, drawn in to the neck; once it snaps the tip closes to a point
			float falloff = (1f - v) * (1f - v);
			float r = radius * (neck + (1f - neck) * falloff);
			r *= 1f + 0.07f * Mth.sin(v * 8f - time * 0.9f);
			if (snap > 0f) {
				r *= 1f - Mth.clamp(snap * 5f, 0f, 1f) * v * v * v;
			}
			float[][] ring = new float[SIDES + 1][6];
			for (int i = 0; i <= SIDES; i++) {
				float th = Mth.TWO_PI * i / SIDES;
				float cs = Mth.cos(th), sn = Mth.sin(th);
				double nx = side.x * cs + up.x * sn, ny = side.y * cs + up.y * sn, nz = side.z * cs + up.z * sn;
				ring[i][0] = (float) (c.x + nx * r);
				ring[i][1] = (float) (c.y + ny * r);
				ring[i][2] = (float) (c.z + nz * r);
				ring[i][3] = (float) nx;
				ring[i][4] = (float) ny;
				ring[i][5] = (float) nz;
			}
			if (prev != null) {
				float va = Mth.lerp((j - 1) / (float) RINGS, V0, V1), vb = Mth.lerp(v, V0, V1);
				for (int i = 0; i < SIDES; i++) {
					float ua = Mth.lerp(i / (float) SIDES, U0, U1), ub = Mth.lerp((i + 1) / (float) SIDES, U0, U1);
					vertex(vc, last, prev[i], ua, va, light);
					vertex(vc, last, prev[i + 1], ub, va, light);
					vertex(vc, last, ring[i + 1], ub, vb, light);
					vertex(vc, last, ring[i], ua, vb, light);
				}
			}
			prev = ring;
		}
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose last, float[] p, float u, float v, int light) {
		vc.addVertex(last, p[0], p[1], p[2]).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, p[3], p[4],
				p[5]);
	}

	/** The quadratic through {@code a}, {@code m} at the middle and {@code b}, at {@code s} 0..1. */
	private static Vec3 curve(Vec3 a, Vec3 m, Vec3 b, float s) {
		Vec3 c = m.scale(2.0).subtract(a.add(b).scale(0.5)); // the control point that puts the curve through m
		double k = 1.0 - s;
		return a.scale(k * k).add(c.scale(2.0 * k * s)).add(b.scale(s * s));
	}
}
