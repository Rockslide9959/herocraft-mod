package com.projecthero.mod.client.render;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * v0.17.3 (user: "make [the Saiyan tail] smooth and round"): a round tube through a chain of joints. A Catmull-Rom curve through
 * the joints, sampled {@code sub} times per link; a ring of {@code sides} vertices at every sample, its frame carried down the
 * curve (parallel transport, so the tube never twists); real outward normals, so the entity shader lights it round; a short stub
 * back into the root and a rounded (half-sphere) end cap. Units are the pose's own (blocks in a model's space).
 */
public final class TubeMesh {
	private TubeMesh() {
	}

	/** Radius (pose units) at {@code t}, 0 = root .. 1 = tip. */
	@FunctionalInterface
	public interface Radius {
		float at(float t);
	}

	/** ARGB at {@code t} (0 = root .. 1 = tip) for a vertex facing {@code side} (0..1 round the ring). */
	@FunctionalInterface
	public interface Colour {
		int at(float t, float side);
	}

	/** The smooth centre line: {@code (pts.length - 1) * sub + 1} samples. */
	public static Vector3f[] curve(Vector3f[] pts, int sub) {
		int n = pts.length - 1;
		Vector3f[] out = new Vector3f[n * sub + 1];
		for (int i = 0; i < n; i++) {
			Vector3f p0 = i == 0 ? new Vector3f(pts[0]).mul(2f).sub(pts[1]) : pts[i - 1];
			Vector3f p1 = pts[i], p2 = pts[i + 1];
			Vector3f p3 = i + 2 <= n ? pts[i + 2] : new Vector3f(pts[n]).mul(2f).sub(pts[n - 1]);
			for (int k = 0; k < sub; k++) {
				out[i * sub + k] = catmull(p0, p1, p2, p3, k / (float) sub);
			}
		}
		out[n * sub] = new Vector3f(pts[n]);
		return out;
	}

	private static Vector3f catmull(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
		float t2 = t * t, t3 = t2 * t;
		float a = -0.5f * t3 + t2 - 0.5f * t, b = 1.5f * t3 - 2.5f * t2 + 1f, c = -1.5f * t3 + 2f * t2 + 0.5f * t, d = 0.5f * t3 - 0.5f * t2;
		return new Vector3f(p0).mul(a).add(new Vector3f(p1).mul(b)).add(new Vector3f(p2).mul(c)).add(new Vector3f(p3).mul(d));
	}

	/**
	 * Draws the tube.
	 *
	 * @param stub how far (pose units) the tube carries on back past the root, into the body, so it never shows a gap there
	 */
	public static void draw(PoseStack.Pose pose, VertexConsumer vc, Vector3f[] pts, int sub, int sides, float stub, Radius radius, Colour colour,
			int light) {
		Vector3f[] c = curve(pts, sub);
		int m = c.length;
		Vector3f[] tan = new Vector3f[m];
		for (int i = 0; i < m; i++) {
			Vector3f d = new Vector3f(c[Math.min(i + 1, m - 1)]).sub(c[Math.max(i - 1, 0)]);
			tan[i] = d.lengthSquared() < 1e-12f ? (i > 0 ? new Vector3f(tan[i - 1]) : new Vector3f(0, 1, 0)) : d.normalize();
		}
		// the first ring's frame: "up" in the model (y is down there) squeezed square to the curve, or sideways if the curve runs up
		Vector3f nrm = new Vector3f(0, -1, 0);
		if (Math.abs(nrm.dot(tan[0])) > 0.9f) {
			nrm.set(1, 0, 0);
		}
		int caps = 4;
		int rings = m + 1 + caps;
		Vector3f[] ctr = new Vector3f[rings];
		Vector3f[] nn = new Vector3f[rings];
		Vector3f[] bb = new Vector3f[rings];
		Vector3f[] tt = new Vector3f[rings];
		float[] rad = new float[rings];
		float[] along = new float[rings];
		for (int i = 0; i < m; i++) {
			Vector3f t = tan[i];
			nrm = new Vector3f(nrm).sub(new Vector3f(t).mul(nrm.dot(t)));
			if (nrm.lengthSquared() < 1e-10f) {
				nrm = Math.abs(t.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, -1, 0);
				nrm.sub(new Vector3f(t).mul(nrm.dot(t)));
			}
			nrm.normalize();
			int r = i + 1;
			ctr[r] = c[i];
			tt[r] = t;
			nn[r] = new Vector3f(nrm);
			bb[r] = new Vector3f(t).cross(nrm).normalize();
			along[r] = i / (float) (m - 1);
			rad[r] = radius.at(along[r]);
		}
		// the stub into the body
		ctr[0] = new Vector3f(c[0]).sub(new Vector3f(tan[0]).mul(stub));
		tt[0] = tan[0];
		nn[0] = nn[1];
		bb[0] = bb[1];
		rad[0] = rad[1];
		along[0] = 0f;
		// the rounded tip: a half sphere of the last ring's radius
		float rEnd = rad[m];
		for (int k = 1; k <= caps; k++) {
			float a = k / (float) caps * Mth.HALF_PI;
			int r = m + k;
			ctr[r] = new Vector3f(c[m - 1]).add(new Vector3f(tan[m - 1]).mul(rEnd * Mth.sin(a)));
			tt[r] = tan[m - 1];
			nn[r] = nn[m];
			bb[r] = bb[m];
			rad[r] = k == caps ? 0f : rEnd * Mth.cos(a);
			along[r] = 1f;
		}
		Vector3f[] prevP = null, prevN = null;
		int[] prevC = null;
		for (int r = 0; r < rings; r++) {
			Vector3f[] ps = new Vector3f[sides + 1];
			Vector3f[] ns = new Vector3f[sides + 1];
			int[] cs = new int[sides + 1];
			// the cap rings' normals tilt out along the curve, like a sphere's
			float capTilt = r > m ? (r - m) / (float) caps * Mth.HALF_PI : 0f;
			for (int s = 0; s <= sides; s++) {
				float a = s / (float) sides * Mth.TWO_PI;
				Vector3f out = new Vector3f(nn[r]).mul(Mth.cos(a)).add(new Vector3f(bb[r]).mul(Mth.sin(a)));
				ps[s] = new Vector3f(ctr[r]).add(new Vector3f(out).mul(rad[r]));
				ns[s] = capTilt > 0f ? new Vector3f(out).mul(Mth.cos(capTilt)).add(new Vector3f(tt[r]).mul(Mth.sin(capTilt))).normalize() : out;
				cs[s] = colour.at(along[r], s / (float) sides);
			}
			if (prevP != null) {
				for (int s = 0; s < sides; s++) {
					vertex(vc, pose, prevP[s], prevN[s], prevC[s], light);
					vertex(vc, pose, prevP[s + 1], prevN[s + 1], prevC[s + 1], light);
					vertex(vc, pose, ps[s + 1], ns[s + 1], cs[s + 1], light);
					vertex(vc, pose, ps[s], ns[s], cs[s], light);
				}
			}
			prevP = ps;
			prevN = ns;
			prevC = cs;
		}
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vector3f p, Vector3f n, int argb, int light) {
		vc.addVertex(pose.pose(), p.x, p.y, p.z).setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n.x, n.y, n.z);
	}
}
