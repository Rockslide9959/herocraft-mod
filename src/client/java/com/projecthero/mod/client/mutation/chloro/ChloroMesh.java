package com.projecthero.mod.client.mutation.chloro;

import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1 Chlorokinesis: the procedural plant geometry every Chlorokinesis effect is built from -- tapering tubes along a
 * curve (vines, roots, stems, spikes), leaf cards, opening flowers and soft camera-facing spore puffs. Textures come from
 * {@code scratchpad/chlorokinesis_v0171/gen_chloro_v0171.js}.
 */
public final class ChloroMesh {
	public static final ResourceLocation VINE = ProjectHeroMod.id("textures/entity/mutation/p22_vine.png");
	public static final ResourceLocation ROOT = ProjectHeroMod.id("textures/entity/mutation/p22_root.png");
	public static final ResourceLocation LEAF = ProjectHeroMod.id("textures/entity/mutation/p22_leaf.png");
	public static final ResourceLocation PETAL = ProjectHeroMod.id("textures/entity/mutation/p22_petal.png");
	public static final ResourceLocation SPORE = ProjectHeroMod.id("textures/entity/mutation/p22_spore.png");
	public static final int FULL_BRIGHT = 0xF000F0;
	private static final Vector3f UP = new Vector3f(0, 1, 0);

	private ChloroMesh() {
	}

	public static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int r, int g, int b,
			int a, int light, float nx, float ny, float nz) {
		vc.addVertex(pose, x, y, z).setColor(r, g, b, Mth.clamp(a, 0, 255)).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(pose, nx, ny, nz);
	}

	/** Any unit vector at right angles to {@code d}. */
	private static Vector3f perp(Vector3f d) {
		Vector3f a = Math.abs(d.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
		return a.cross(d, new Vector3f()).normalize();
	}

	/**
	 * A tube through {@code pts} -- radius {@code r0} at the first point tapering to {@code r1} at the last -- with
	 * {@code sides} faces, the texture's v running along it ({@code vPerBlock} tiles a block). Frames are carried along the
	 * curve (parallel transport) so a twisting root does not pinch.
	 */
	public static void tube(VertexConsumer vc, PoseStack.Pose pose, List<Vector3f> pts, float r0, float r1, int sides, float vPerBlock,
			int r, int g, int b, int a, int light) {
		int n = pts.size();
		if (n < 2) {
			return;
		}
		Vector3f[] ringN = new Vector3f[n];
		Vector3f[] ringB = new Vector3f[n];
		float[] vAt = new float[n];
		Vector3f prevT = new Vector3f(pts.get(1)).sub(pts.get(0));
		if (prevT.lengthSquared() < 1.0e-8f) {
			prevT.set(0, 1, 0);
		}
		prevT.normalize();
		Vector3f nrm = perp(prevT);
		float acc = 0;
		for (int i = 0; i < n; i++) {
			Vector3f t;
			if (i == n - 1) {
				t = new Vector3f(pts.get(i)).sub(pts.get(i - 1));
			} else {
				t = new Vector3f(pts.get(i + 1)).sub(pts.get(i));
			}
			if (t.lengthSquared() < 1.0e-8f) {
				t.set(prevT);
			}
			t.normalize();
			// transport the normal: remove its component along the new tangent
			nrm.sub(new Vector3f(t).mul(nrm.dot(t)));
			if (nrm.lengthSquared() < 1.0e-6f) {
				nrm = perp(t);
			}
			nrm.normalize();
			ringN[i] = new Vector3f(nrm);
			ringB[i] = new Vector3f(t).cross(nrm).normalize();
			if (i > 0) {
				acc += pts.get(i).distance(pts.get(i - 1));
			}
			vAt[i] = acc * vPerBlock;
			prevT = t;
		}
		for (int i = 0; i < n - 1; i++) {
			float fa = (float) i / (n - 1), fb = (float) (i + 1) / (n - 1);
			float ra = Mth.lerp(fa, r0, r1), rb = Mth.lerp(fb, r0, r1);
			Vector3f pa = pts.get(i), pb = pts.get(i + 1);
			for (int s = 0; s < sides; s++) {
				float t0 = Mth.TWO_PI * s / sides, t1 = Mth.TWO_PI * (s + 1) / sides;
				float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
				float u0 = (float) s / sides, u1 = (float) (s + 1) / sides;
				Vector3f na0 = new Vector3f(ringN[i]).mul(c0).add(new Vector3f(ringB[i]).mul(s0));
				Vector3f na1 = new Vector3f(ringN[i]).mul(c1).add(new Vector3f(ringB[i]).mul(s1));
				Vector3f nb0 = new Vector3f(ringN[i + 1]).mul(c0).add(new Vector3f(ringB[i + 1]).mul(s0));
				Vector3f nb1 = new Vector3f(ringN[i + 1]).mul(c1).add(new Vector3f(ringB[i + 1]).mul(s1));
				vertex(vc, pose, pa.x + na0.x * ra, pa.y + na0.y * ra, pa.z + na0.z * ra, u0, vAt[i], r, g, b, a, light, na0.x, na0.y, na0.z);
				vertex(vc, pose, pb.x + nb0.x * rb, pb.y + nb0.y * rb, pb.z + nb0.z * rb, u0, vAt[i + 1], r, g, b, a, light, nb0.x, nb0.y, nb0.z);
				vertex(vc, pose, pb.x + nb1.x * rb, pb.y + nb1.y * rb, pb.z + nb1.z * rb, u1, vAt[i + 1], r, g, b, a, light, nb1.x, nb1.y, nb1.z);
				vertex(vc, pose, pa.x + na1.x * ra, pa.y + na1.y * ra, pa.z + na1.z * ra, u1, vAt[i], r, g, b, a, light, na1.x, na1.y, na1.z);
			}
		}
	}

	/**
	 * A leaf card (the {@link #LEAF} texture points up its v axis): stalk at {@code base}, pointing along {@code along},
	 * {@code len} long and {@code wid} wide, its face spread along {@code side}. Draw into a no-cull buffer.
	 */
	public static void leaf(VertexConsumer vc, PoseStack.Pose pose, Vector3f base, Vector3f along, Vector3f side, float len, float wid,
			int r, int g, int b, int a, int light) {
		leaf(vc, pose, base, along, side, len, wid, r, g, b, a, light, null);
	}

	/** {@link #leaf} with a fixed lighting normal (null = the card's own): flowers face up so they never shade dark. */
	public static void leaf(VertexConsumer vc, PoseStack.Pose pose, Vector3f base, Vector3f along, Vector3f side, float len, float wid,
			int r, int g, int b, int a, int light, Vector3f normal) {
		Vector3f d = new Vector3f(along).normalize(len);
		Vector3f s = new Vector3f(side).normalize(wid * 0.5f);
		Vector3f n = normal != null ? normal : new Vector3f(d).cross(s).normalize();
		Vector3f tip = new Vector3f(base).add(d);
		vertex(vc, pose, base.x - s.x, base.y - s.y, base.z - s.z, 0, 1, r, g, b, a, light, n.x, n.y, n.z);
		vertex(vc, pose, base.x + s.x, base.y + s.y, base.z + s.z, 1, 1, r, g, b, a, light, n.x, n.y, n.z);
		vertex(vc, pose, tip.x + s.x, tip.y + s.y, tip.z + s.z, 1, 0, r, g, b, a, light, n.x, n.y, n.z);
		vertex(vc, pose, tip.x - s.x, tip.y - s.y, tip.z - s.z, 0, 0, r, g, b, a, light, n.x, n.y, n.z);
	}

	/**
	 * A flower head at {@code c} facing up: {@code petals} petals ({@link #PETAL}, tinted) opened by {@code open} (0 =
	 * a closed bud pointing up, 1 = flat out, more = drooping as it wilts), {@code size} long, turned by {@code spin}.
	 */
	public static void flower(VertexConsumer vc, PoseStack.Pose pose, Vector3f c, int petals, float open, float size, float spin, int r,
			int g, int b, int a, int light) {
		float tilt = Mth.clamp(open, 0, 1.6f) * Mth.HALF_PI; // 0 = up, pi/2 = out flat
		for (int i = 0; i < petals; i++) {
			float ang = spin + Mth.TWO_PI * i / petals;
			Vector3f out = new Vector3f(Mth.cos(ang), 0, Mth.sin(ang));
			Vector3f along = new Vector3f(out).mul(Mth.sin(tilt)).add(0, Mth.cos(tilt), 0);
			Vector3f side = new Vector3f(-out.z, 0, out.x);
			leaf(vc, pose, c, along, side, size, size * 0.7f, r, g, b, a, light, UP);
		}
	}

	/**
	 * The soft-glow buffer for spores and ground rings: additive and never writing depth, so a big puff never hides the
	 * vines and roots behind it. Colours drawn into it are premultiplied by their alpha ({@link #billboard}, {@link #flatRing}).
	 */
	public static net.minecraft.client.renderer.RenderType glow() {
		return net.minecraft.client.renderer.RenderType.eyes(SPORE);
	}

	/** A camera-facing square of half-size {@code h} at {@code c}, rotated by {@code roll} round the view axis (glow colours). */
	public static void billboard(VertexConsumer vc, PoseStack.Pose pose, org.joml.Quaternionf cam, Vector3f c, float h, float roll, int r,
			int g, int b, int a, int light) {
		r = r * a / 255;
		g = g * a / 255;
		b = b * a / 255;
		Vector3f right = cam.transform(new Vector3f(Mth.cos(roll), Mth.sin(roll), 0)).mul(h);
		Vector3f up = cam.transform(new Vector3f(-Mth.sin(roll), Mth.cos(roll), 0)).mul(h);
		Vector3f n = cam.transform(new Vector3f(0, 0, 1));
		vertex(vc, pose, c.x - right.x - up.x, c.y - right.y - up.y, c.z - right.z - up.z, 0, 1, r, g, b, a, light, n.x, n.y, n.z);
		vertex(vc, pose, c.x + right.x - up.x, c.y + right.y - up.y, c.z + right.z - up.z, 1, 1, r, g, b, a, light, n.x, n.y, n.z);
		vertex(vc, pose, c.x + right.x + up.x, c.y + right.y + up.y, c.z + right.z + up.z, 1, 0, r, g, b, a, light, n.x, n.y, n.z);
		vertex(vc, pose, c.x - right.x + up.x, c.y - right.y + up.y, c.z - right.z + up.z, 0, 0, r, g, b, a, light, n.x, n.y, n.z);
	}

	/** A flat ring on the ground (y = {@code y}) between {@code rIn} and {@code rOut}, alpha fading outward. */
	public static void flatRing(VertexConsumer vc, PoseStack.Pose pose, float y, float rIn, float rOut, int segs, int r, int g, int b,
			int aIn, int aOut, int light) {
		int ri = r * aIn / 255, gi = g * aIn / 255, bi = b * aIn / 255;
		int ro = r * aOut / 255, go = g * aOut / 255, bo = b * aOut / 255;
		for (int s = 0; s < segs; s++) {
			float t0 = Mth.TWO_PI * s / segs, t1 = Mth.TWO_PI * (s + 1) / segs;
			float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
			vertex(vc, pose, c0 * rIn, y, s0 * rIn, 0.5f, 0.5f, ri, gi, bi, aIn, light, 0, 1, 0);
			vertex(vc, pose, c1 * rIn, y, s1 * rIn, 0.5f, 0.5f, ri, gi, bi, aIn, light, 0, 1, 0);
			vertex(vc, pose, c1 * rOut, y, s1 * rOut, 0.5f, 0.5f, ro, go, bo, aOut, light, 0, 1, 0);
			vertex(vc, pose, c0 * rOut, y, s0 * rOut, 0.5f, 0.5f, ro, go, bo, aOut, light, 0, 1, 0);
		}
	}

	/** Smoothstep 0..1. */
	public static float smooth(float t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}
}
