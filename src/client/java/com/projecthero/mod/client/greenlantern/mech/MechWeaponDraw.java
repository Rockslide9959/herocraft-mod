package com.projecthero.mod.client.greenlantern.mech;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.projecthero.mod.client.greenlantern.HardLightDraw;
import com.projecthero.mod.client.maxsteel.TurboDraw;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * v0.17.1: the Hard-Light Mech's construct-combo weapons, built in code as real meshes (not boxes): the giant
 * <b>hard-light katana</b> (a curved single-edged blade with a shinogi ridge, a wavy hamon, a kissaki with its yokote
 * line, a habaki, an oval tsuba with the Lantern emblem cut through it, a diamond-wrapped tsuka and a kashira), the
 * spiral <b>Giga Drill</b> (a fluted cone that spins) and the iaido crescent. Everything is full-bright, translucent and
 * tinted per vertex ({@code entityTranslucentEmissive} over white, via {@link HardLightDraw#buffer}). Units are blocks.
 *
 * <p>Katana frame: the origin is the middle of the tsuba, the blade runs along -Z, the spine is +Y (the edge faces -Y),
 * the flats face +/-X; the grip runs back along +Z.
 */
public final class MechWeaponDraw {
	// ---- katana proportions (blocks)
	public static final float BLADE_LEN = 5.7f;
	public static final float BLADE_W = 0.36f;
	public static final float SORI = 0.30f;
	public static final float KISSAKI = 0.15f;
	public static final float GRIP_LEN = 1.75f;
	public static final float TSUBA_Z = 0f;

	private MechWeaponDraw() {
	}

	// ================================================================ primitives

	private static int argb(int rgb, float a) {
		return (Mth.clamp(Math.round(a * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	/** A quad with a colour per corner (ARGB). Corners in order round the face. */
	public static void quad(VertexConsumer vc, PoseStack.Pose pose, float[] a, float[] b, float[] c, float[] d, int ca, int cb, int cc, int cd) {
		// hard light is self-lit: every face gets the normal that ends up pointing world-up after the pose (the entity
		// shader's two lights both come from above), so no face of a mesh is shaded dark whatever its winding
		if (pose != lastPose || !pose.normal().equals(lastNormal)) {
			lastPose = pose;
			lastNormal.set(pose.normal());
			org.joml.Vector3f up = new org.joml.Matrix3f(pose.normal()).invert().transform(new org.joml.Vector3f(0f, 1f, 0f)).normalize();
			upX = up.x;
			upY = up.y;
			upZ = up.z;
		}
		float nx = upX, ny = upY, nz = upZ;
		v(vc, pose, a, ca, 0, 0, nx, ny, nz);
		v(vc, pose, b, cb, 0, 1, nx, ny, nz);
		v(vc, pose, c, cc, 1, 1, nx, ny, nz);
		v(vc, pose, d, cd, 1, 0, nx, ny, nz);
	}

	private static PoseStack.Pose lastPose;
	private static final org.joml.Matrix3f lastNormal = new org.joml.Matrix3f();
	private static float upX, upY = 1f, upZ;

	public static void quad(VertexConsumer vc, PoseStack.Pose pose, float[] a, float[] b, float[] c, float[] d, int col) {
		quad(vc, pose, a, b, c, d, col, col, col, col);
	}

	private static void v(VertexConsumer vc, PoseStack.Pose pose, float[] p, int c, float u, float w, float nx, float ny, float nz) {
		vc.addVertex(pose, p[0], p[1], p[2]).setColor((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF).setUv(u, w)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
	}

	/** A box centred on the origin (half-extents), every face self-lit like the meshes ({@link #quad}). */
	public static void cube(VertexConsumer vc, PoseStack.Pose P, float hx, float hy, float hz, int argb) {
		float[] a = p(-hx, -hy, -hz), b = p(hx, -hy, -hz), c = p(hx, hy, -hz), d = p(-hx, hy, -hz);
		float[] e = p(-hx, -hy, hz), f = p(hx, -hy, hz), g = p(hx, hy, hz), h = p(-hx, hy, hz);
		quad(vc, P, a, d, c, b, argb);
		quad(vc, P, e, f, g, h, argb);
		quad(vc, P, a, b, f, e, argb);
		quad(vc, P, d, h, g, c, argb);
		quad(vc, P, a, e, h, d, argb);
		quad(vc, P, b, c, g, f, argb);
	}

	private static void cube(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, int rgb, float alpha) {
		pose.pushPose();
		pose.translate(x, y, z);
		cube(vc, pose.last(), hx, hy, hz, argb(rgb, alpha));
		cube(vc, pose.last(), hx + 0.012f, hy + 0.012f, hz + 0.012f, argb(HardLightDraw.tint(PALE), 0.25f * alpha));
		pose.popPose();
	}

	private static float[] p(float x, float y, float z) {
		return new float[] { x, y, z };
	}

	private static int mix(int a, int b, float t) {
		t = Mth.clamp(t, 0f, 1f);
		int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}

	// ================================================================ katana

	/** Centre-line height (toward the spine) of the curved blade at u (0 = the habaki, 1 = the tip). */
	public static float curve(float u) {
		return SORI * u * u;
	}

	/** Where the blade's middle is at u, in the katana frame (the slash trail reads this). */
	public static float[] bladePoint(float u, float across) {
		float s = u * BLADE_LEN;
		return p(0f, curve(u) + across * BLADE_W * 0.5f, TSUBA_Z - 0.24f - s);
	}

	/**
	 * The whole katana. {@code reveal} 0..1 = how much of the blade is out (drawing / sheathing: it forms out of the
	 * tsuba, a bright ring at its front); {@code fit} 0..1 fades the mounts (tsuba, grip) in and out; {@code age} drives
	 * the sheen that sweeps along the edge.
	 */
	public static void katana(VertexConsumer vc, PoseStack pose, float reveal, float fit, float age, boolean golden) {
		HardLightDraw.golden = golden;
		try {
			if (fit > 0.01f) {
				mounts(vc, pose, fit, age);
			}
			if (reveal > 0.01f) {
				blade(vc, pose, reveal, age, fit);
			}
		} finally {
			HardLightDraw.golden = false;
		}
	}

	private static final int GREEN = HardLightDraw.GREEN;
	private static final int DEEP = 0x1A9C4A;
	private static final int PALE = 0xC8FFD8;
	private static final int WHITE = 0xF4FFF6;

	/** One cross-section of the blade at u: six points (spine peak, spine shoulders, shinogi ridges, edge). */
	private static float[][] section(float u, float scale) {
		float w = BLADE_W * (1f - 0.22f * u) * scale; // a slight taper to the point
		float k = 1f - KISSAKI;
		float spineTop = w * 0.5f, edge = -w * 0.5f;
		float thick = 0.095f * (1f - 0.4f * u) * scale; // full width of the shinogi
		float shoulder = thick * 0.38f;
		float ridgeY = spineTop - w * 0.32f;
		if (u > k) { // the kissaki: the edge sweeps up (fukura), the spine drops a little, all meeting at the point
			float v = (u - k) / KISSAKI;
			float tipY = w * 0.30f;
			edge = Mth.lerp((float) (1 - Math.sqrt(1 - v * v)), edge, tipY);
			spineTop = Mth.lerp(v * v * v, spineTop, tipY);
			ridgeY = Mth.lerp(v, ridgeY, tipY);
			thick *= 1f - 0.9f * v;
			shoulder *= 1f - 0.9f * v;
		}
		float cy = curve(u);
		float z = TSUBA_Z - 0.24f - u * BLADE_LEN;
		return new float[][] {
				p(0f, cy + spineTop + 0.012f * scale, z), // 0 spine peak (iori-mune)
				p(-shoulder, cy + spineTop - 0.02f * scale, z), // 1
				p(-thick * 0.5f, cy + ridgeY, z), // 2 shinogi (right flat)
				p(0f, cy + edge, z), // 3 edge
				p(thick * 0.5f, cy + ridgeY, z), // 4 shinogi (left flat)
				p(shoulder, cy + spineTop - 0.02f * scale, z), // 5
		};
	}

	private static float hamonY(float u) {
		// notare waves with a gunome ripple; past the yokote the boshi turns back toward the spine
		float s = u * BLADE_LEN;
		return 0.23f + 0.055f * Mth.sin(s * 3.1f) + 0.03f * Mth.sin(s * 7.7f + 1.3f);
	}

	private static void blade(VertexConsumer vc, PoseStack pose, float reveal, float age, float fit) {
		PoseStack.Pose P = pose.last();
		int n = 44;
		float sweep = ((age * 0.045f) % 1.8f) - 0.4f; // the sheen's place along the blade
		float shown = Math.min(1f, reveal);
		float[][] prev = null;
		float prevU = 0f;
		for (int i = 0; i <= n; i++) {
			float u = Math.min(shown, i / (float) n);
			float[][] s = section(u, 1f);
			if (prev != null) {
				float sh0 = sheen(prevU, sweep), sh1 = sheen(u, sweep);
				// faces: spine roof (0-1, 5-0), shinogi-ji (1-2, 4-5), hira down to the edge (2-3, 3-4)
				for (int f = 0; f < 6; f++) {
					int a = f, b = (f + 1) % 6;
					int c0 = faceCol(f, prevU, sh0), c1 = faceCol(f, u, sh1);
					int e0 = faceCol(f, prevU, sh0), e1 = faceCol(f, u, sh1);
					if (f == 2 || f == 3) {
						// the flat from the ridge down to the edge, split along the hamon: green steel above it (ji), the
						// pale hardened edge below it (ha) -- the wavy boundary is what reads as a real temper line
						int r = f == 2 ? 2 : 4;
						float sign = f == 2 ? -1f : 1f;
						float h0 = hamonAt(prevU), h1 = hamonAt(u);
						float[] m0 = along(prev[3], prev[r], h0, 0f), m1 = along(s[3], s[r], h1, 0f);
						int ce0 = edgeCol(prevU, sh0), ce1 = edgeCol(u, sh1);
						int ch0 = argb(mix(HardLightDraw.tint(PALE), WHITE, 0.3f), 0.85f), ch1 = ch0;
						if (f == 2) {
							quad(vc, P, prev[2], s[2], m1, m0, c0, c1, c1, c0);
							quad(vc, P, m0, m1, s[3], prev[3], ch0, ch1, ce1, ce0);
						} else {
							quad(vc, P, prev[3], s[3], m1, m0, ce0, ce1, ch1, ch0);
							quad(vc, P, m0, m1, s[4], prev[4], c0, c1, c1, c0);
						}
					} else {
						quad(vc, P, prev[a], s[a], s[b], prev[b], c0, c1, e1, e0);
					}
				}
				hamon(vc, P, prev, s, prevU, u, sh0, sh1);
				lines(vc, P, prev, s, sh0, sh1);
			}
			prev = s;
			prevU = u;
			if (u >= shown) {
				break;
			}
		}
		// the yokote: the bright line across the flats where the point begins
		if (shown > 1f - KISSAKI) {
			float[][] y = section(1f - KISSAKI, 1f);
			float[][] y2 = section(1f - KISSAKI + 0.006f, 1f);
			int c = argb(WHITE, 0.95f);
			quad(vc, P, off(y[2], -0.006f), off(y[3], -0.006f), off(y2[3], -0.006f), off(y2[2], -0.006f), c);
			quad(vc, P, off(y[4], 0.006f), off(y2[4], 0.006f), off(y2[3], 0.006f), off(y[3], 0.006f), c);
		}
		// a soft glow shell round the whole blade
		prev = null;
		for (int i = 0; i <= 22; i++) {
			float u = Math.min(shown, i / 22f);
			float[][] s = section(u, 1.3f);
			if (prev != null) {
				int c = argb(HardLightDraw.tint(GREEN), 0.1f);
				for (int f = 0; f < 6; f++) {
					int a = f, b = (f + 1) % 6;
					quad(vc, P, prev[a], s[a], s[b], prev[b], c);
				}
			}
			prev = s;
			if (u >= shown) {
				break;
			}
		}
		// drawing / sheathing: a ring of light at the blade's growing front
		if (reveal < 0.999f) {
			float[] f = bladePoint(shown, 0f);
			pose.pushPose();
			pose.translate(f[0], f[1], f[2]);
			for (int i = 0; i < 8; i++) {
				pose.pushPose();
				pose.mulPose(Axis.ZP.rotationDegrees(i * 45f + age * 20f));
				pose.translate(0f, 0.34f, 0f);
				TurboDraw.box(vc, pose.last(), 0.15f, 0.03f, 0.03f, 0xF0FFF4, 0.85f);
				pose.popPose();
			}
			TurboDraw.box(vc, pose.last(), 0.28f, 0.28f, 0.05f, HardLightDraw.tint(GREEN), 0.35f);
			pose.popPose();
		}
	}

	private static float[] off(float[] q, float dx) {
		return p(q[0] + dx, q[1], q[2]);
	}

	private static float sheen(float u, float sweep) {
		float d = Math.abs(u - sweep);
		return d > 0.12f ? 0f : 1f - d / 0.12f;
	}

	private static int faceCol(int f, float u, float sheen) {
		int base = switch (f) {
			case 0, 5 -> HardLightDraw.tint(DEEP); // the spine roof
			case 1, 4 -> HardLightDraw.tint(GREEN); // shinogi-ji
			default -> HardLightDraw.tint(GREEN);
		};
		float a = f == 0 || f == 5 ? 0.82f : 0.72f;
		return argb(mix(base, WHITE, 0.18f + sheen * 0.8f), Math.min(1f, a + 0.25f * sheen));
	}

	private static int edgeCol(float u, float sheen) {
		return argb(mix(HardLightDraw.tint(PALE), WHITE, 0.55f + 0.45f * sheen), 0.92f);
	}

	/** The hamon: a wavy bright line along both flats between the edge and the ridge. */
	/** The hamon's place across the flat at u (0 = the edge, 1 = the ridge), the boshi turning back round the point. */
	private static float hamonAt(float u) {
		float h = hamonY(u);
		float k = 1f - KISSAKI;
		return u > k ? Mth.lerp((u - k) / KISSAKI, h, 0.55f) : h;
	}

	private static void hamon(VertexConsumer vc, PoseStack.Pose P, float[][] a, float[][] b, float u0, float u1, float sh0, float sh1) {
		float h0 = hamonAt(u0), h1 = hamonAt(u1);
		float d = 0.035f;
		int c0 = argb(mix(WHITE, 0xFFFFFF, sh0), 0.92f), c1 = argb(mix(WHITE, 0xFFFFFF, sh1), 0.92f);
		for (int side = 0; side < 2; side++) {
			int r = side == 0 ? 2 : 4; // ridge point index on this flat
			float sign = side == 0 ? -1f : 1f;
			float[] lo0 = along(a[3], a[r], h0 - d, sign), hi0 = along(a[3], a[r], h0 + d, sign);
			float[] lo1 = along(b[3], b[r], h1 - d, sign), hi1 = along(b[3], b[r], h1 + d, sign);
			if (side == 0) {
				quad(vc, P, lo0, lo1, hi1, hi0, c0, c1, c1, c0);
			} else {
				quad(vc, P, lo0, hi0, hi1, lo1, c0, c0, c1, c1);
			}
		}
	}

	/** The point a fraction {@code t} of the way from the edge {@code e} to the ridge {@code r}, pushed off the flat. */
	private static float[] along(float[] e, float[] r, float t, float sign) {
		return p(e[0] + (r[0] - e[0]) * t + sign * 0.006f, e[1] + (r[1] - e[1]) * t, e[2] + (r[2] - e[2]) * t);
	}

	/** The cutting edge and the shinogi ridges: thin bright lines. */
	private static void lines(VertexConsumer vc, PoseStack.Pose P, float[][] a, float[][] b, float sh0, float sh1) {
		int ce0 = argb(WHITE, 0.95f), ce1 = argb(WHITE, 0.95f);
		float w = 0.018f;
		quad(vc, P, p(a[3][0] - w, a[3][1] - 0.004f, a[3][2]), p(b[3][0] - w, b[3][1] - 0.004f, b[3][2]),
				p(b[3][0] + w, b[3][1] - 0.004f, b[3][2]), p(a[3][0] + w, a[3][1] - 0.004f, a[3][2]), ce0, ce1, ce1, ce0);
		int cr0 = argb(mix(PALE, WHITE, sh0), 0.8f), cr1 = argb(mix(PALE, WHITE, sh1), 0.8f);
		for (int r : new int[] { 2, 4 }) {
			float s = r == 2 ? -0.004f : 0.004f;
			quad(vc, P, p(a[r][0] + s, a[r][1] - 0.012f, a[r][2]), p(b[r][0] + s, b[r][1] - 0.012f, b[r][2]),
					p(b[r][0] + s, b[r][1] + 0.012f, b[r][2]), p(a[r][0] + s, a[r][1] + 0.012f, a[r][2]), cr0, cr1, cr1, cr0);
		}
		// the spine peak
		quad(vc, P, p(a[0][0] - 0.01f, a[0][1], a[0][2]), p(b[0][0] - 0.01f, b[0][1], b[0][2]), p(b[0][0] + 0.01f, b[0][1], b[0][2]),
				p(a[0][0] + 0.01f, a[0][1], a[0][2]), argb(PALE, 0.7f));
	}

	/** Habaki, seppa, tsuba (emblem cut through it), tsuka with its wrap, kashira. */
	private static void mounts(VertexConsumer vc, PoseStack pose, float fit, float age) {
		// habaki: the collar at the blade's root
		cube(vc, pose, 0f, 0f, TSUBA_Z - 0.15f, 0.07f, BLADE_W * 0.56f, 0.12f, 0xE8FFEE, 0.95f * fit);
		// seppa
		cube(vc, pose, 0f, 0f, TSUBA_Z - 0.05f, 0.1f, 0.24f, 0.015f, HardLightDraw.tint(PALE), 0.85f * fit);
		cube(vc, pose, 0f, 0f, TSUBA_Z + 0.05f, 0.1f, 0.24f, 0.015f, HardLightDraw.tint(PALE), 0.85f * fit);
		tsuba(vc, pose, fit, age);
		tsuka(vc, pose, fit);
	}

	private static void tsuba(VertexConsumer vc, PoseStack pose, float fit, float age) {
		PoseStack.Pose P = pose.last();
		int n = 28;
		float rx = 0.46f, ry = 0.54f, hz = 0.035f;
		int face = argb(mix(HardLightDraw.tint(DEEP), 0x000000, 0.35f), 0.85f * fit);
		int rim = argb(HardLightDraw.tint(PALE), 0.95f * fit);
		int ring = argb(HardLightDraw.tint(GREEN), 0.7f * fit);
		// a slightly lobed oval (mokko-ish), the faces as an annulus round a hole for the blade, the rim as a band
		float inR = 0.13f;
		for (int i = 0; i < n; i++) {
			double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
			float l0 = 1f + 0.05f * (float) Math.cos(a0 * 4), l1 = 1f + 0.05f * (float) Math.cos(a1 * 4);
			float x0 = (float) Math.cos(a0) * rx * l0, y0 = (float) Math.sin(a0) * ry * l0;
			float x1 = (float) Math.cos(a1) * rx * l1, y1 = (float) Math.sin(a1) * ry * l1;
			float ix0 = (float) Math.cos(a0) * inR, iy0 = (float) Math.sin(a0) * inR * 1.6f;
			float ix1 = (float) Math.cos(a1) * inR, iy1 = (float) Math.sin(a1) * inR * 1.6f;
			quad(vc, P, p(ix0, iy0, -hz), p(x0, y0, -hz), p(x1, y1, -hz), p(ix1, iy1, -hz), ring, face, face, ring);
			quad(vc, P, p(ix0, iy0, hz), p(ix1, iy1, hz), p(x1, y1, hz), p(x0, y0, hz), ring, ring, face, face);
			quad(vc, P, p(x0, y0, -hz), p(x0, y0, hz), p(x1, y1, hz), p(x1, y1, -hz), rim);
			// a raised bright rim line on both faces
			float k = 0.9f;
			quad(vc, P, p(x0 * k, y0 * k, -hz - 0.004f), p(x0, y0, -hz - 0.004f), p(x1, y1, -hz - 0.004f), p(x1 * k, y1 * k, -hz - 0.004f), rim);
			quad(vc, P, p(x0 * k, y0 * k, hz + 0.004f), p(x1 * k, y1 * k, hz + 0.004f), p(x1, y1, hz + 0.004f), p(x0, y0, hz + 0.004f), rim);
		}
		// the Lantern emblem, cut clean through: light pours out of the cut on both faces
		float glow = (0.85f + 0.15f * Mth.sin(age * 0.2f)) * fit;
		for (float z : new float[] { -hz - 0.012f, hz + 0.012f }) {
			pose.pushPose();
			pose.translate(0f, 0f, z);
			float r = 0.24f, t = 0.028f;
			for (int i = 0; i < 12; i++) {
				pose.pushPose();
				pose.mulPose(Axis.ZP.rotationDegrees(i * 30f));
				pose.translate(0f, r, 0f);
				cube(vc, pose.last(), r * 0.27f, t, 0.012f, argb(0xF4FFF6, glow));
				pose.popPose();
			}
			// the two bars, above and below the ring, the width of the tsuba's flat
			for (float y : new float[] { -0.37f, 0.37f }) {
				pose.pushPose();
				pose.translate(0f, y, 0f);
				cube(vc, pose.last(), 0.3f, t * 1.9f, 0.016f, argb(0xF4FFF6, glow));
				cube(vc, pose.last(), 0.34f, t * 2.6f, 0.016f, argb(HardLightDraw.tint(GREEN), 0.4f * glow));
				pose.popPose();
			}
			pose.popPose();
		}
	}

	private static void tsuka(VertexConsumer vc, PoseStack pose, float fit) {
		PoseStack.Pose P = pose.last();
		float hx = 0.095f, hy = 0.125f;
		float z0 = TSUBA_Z + 0.07f, z1 = TSUBA_Z + GRIP_LEN;
		float zc = (z0 + z1) / 2f, hz = (z1 - z0) / 2f;
		// the samegawa core (pale) under the wrap
		pose.pushPose();
		pose.translate(0f, 0.01f, zc);
		TurboDraw.box(vc, pose.last(), hx, hy, hz, HardLightDraw.tint(PALE), 0.55f * fit);
		pose.popPose();
		// the tsuka-ito: crossing bands on every face, leaving diamonds of the core showing
		int wrap = argb(HardLightDraw.tint(DEEP), 0.92f * fit);
		int wrapHi = argb(HardLightDraw.tint(GREEN), 0.95f * fit);
		float pitch = 0.2f;
		int count = (int) ((z1 - z0 - 0.12f) / pitch);
		for (int i = 0; i < count; i++) {
			float za = z0 + 0.08f + i * pitch, zb = za + pitch;
			for (int side = 0; side < 2; side++) {
				float x = side == 0 ? -hx - 0.006f : hx + 0.006f;
				// "X" on the flat: two bands corner to corner
				band(vc, P, p(x, -hy, za), p(x, hy, zb), 0.032f, side == 0 ? -1 : 1, wrap, wrapHi, true);
				band(vc, P, p(x, hy, za), p(x, -hy, zb), 0.032f, side == 0 ? -1 : 1, wrap, wrapHi, true);
				float y = side == 0 ? -hy - 0.006f : hy + 0.006f;
				band(vc, P, p(-hx, y, za), p(hx, y, zb), 0.032f, side == 0 ? -1 : 1, wrap, wrapHi, false);
				band(vc, P, p(hx, y, za), p(-hx, y, zb), 0.032f, side == 0 ? -1 : 1, wrap, wrapHi, false);
			}
		}
		// menuki (the little ornaments under the wrap) and the fuchi collar
		HardLightDraw.glow(vc, pose, -hx - 0.01f, 0.02f, zc - 0.2f, 0.012f, 0.05f, 0.1f, 0.9f * fit);
		HardLightDraw.glow(vc, pose, hx + 0.01f, -0.02f, zc + 0.2f, 0.012f, 0.05f, 0.1f, 0.9f * fit);
		HardLightDraw.part(vc, pose, 0f, 0f, z0 + 0.04f, hx + 0.02f, hy + 0.02f, 0.04f, HardLightDraw.PALE, 0.9f * fit);
		// kashira: the pommel cap
		HardLightDraw.part(vc, pose, 0f, 0f, z1 + 0.04f, hx + 0.025f, hy + 0.025f, 0.06f, HardLightDraw.GREEN, 0.95f * fit);
		HardLightDraw.glow(vc, pose, 0f, 0f, z1 + 0.105f, 0.05f, 0.07f, 0.01f, 0.9f * fit);
	}

	/** A flat band from {@code a} to {@code b} on a face (x-facing when {@code xFace}), {@code w} wide. */
	private static void band(VertexConsumer vc, PoseStack.Pose P, float[] a, float[] b, float w, int sign, int c, int hi, boolean xFace) {
		float dy = b[1] - a[1], dz = b[2] - a[2], dx = b[0] - a[0];
		if (xFace) {
			float l = (float) Math.sqrt(dy * dy + dz * dz);
			float ny = -dz / l * w, nz = dy / l * w;
			float[] q0 = p(a[0], a[1] + ny, a[2] + nz), q1 = p(b[0], b[1] + ny, b[2] + nz), q2 = p(b[0], b[1] - ny, b[2] - nz), q3 = p(a[0], a[1] - ny, a[2] - nz);
			if (sign > 0) {
				quad(vc, P, q0, q1, q2, q3, hi, c, c, hi);
			} else {
				quad(vc, P, q3, q2, q1, q0, c, c, hi, hi);
			}
		} else {
			float l = (float) Math.sqrt(dx * dx + dz * dz);
			float nx = -dz / l * w, nz = dx / l * w;
			float[] q0 = p(a[0] + nx, a[1], a[2] + nz), q1 = p(b[0] + nx, b[1], b[2] + nz), q2 = p(b[0] - nx, b[1], b[2] - nz), q3 = p(a[0] - nx, a[1], a[2] - nz);
			if (sign > 0) {
				quad(vc, P, q3, q2, q1, q0, c, c, hi, hi);
			} else {
				quad(vc, P, q0, q1, q2, q3, hi, c, c, hi);
			}
		}
	}

	// ================================================================ Giga Drill

	/**
	 * The spiral drill, base at the origin, pointing down -Y for {@code len} blocks, base radius {@code r}: three
	 * spiral flutes twisting round a cone, spun by {@code spin} (radians), a heavy collar at the base. {@code alpha}
	 * fades the whole thing (the GIGA DRILL BREAK ghost).
	 */
	public static void drill(VertexConsumer vc, PoseStack pose, float len, float r, float spin, float alpha, boolean golden) {
		HardLightDraw.golden = golden;
		try {
			PoseStack.Pose P = pose.last();
			int rings = 22, seg = 36;
			float turns = 2.3f;
			int ridge = HardLightDraw.tint(PALE), groove = HardLightDraw.tint(DEEP), mid = HardLightDraw.tint(GREEN);
			float[][] prev = null;
			int[] prevC = null;
			for (int i = 0; i <= rings; i++) {
				float s = i / (float) rings;
				float rr = r * (float) Math.pow(1f - s, 0.92) + 0.01f;
				float y = -len * s;
				float[][] ring = new float[seg + 1][];
				int[] cols = new int[seg + 1];
				for (int j = 0; j <= seg; j++) {
					float th = (float) (j * Math.PI * 2 / seg);
					float ph = th * 3f - s * turns * (float) Math.PI * 2f + spin;
					float f = (float) Math.pow(0.5 + 0.5 * Math.cos(ph), 3);
					float rad = rr * (0.8f + 0.26f * f);
					ring[j] = p((float) Math.cos(th) * rad, y, (float) Math.sin(th) * rad);
					int c = f > 0.5f ? mix(mid, ridge, (f - 0.5f) * 2f) : mix(groove, mid, f * 2f);
					float a = (0.55f + 0.4f * f) * alpha;
					cols[j] = argb(mix(c, WHITE, s > 0.9f ? (s - 0.9f) * 8f : 0f), a);
				}
				if (prev != null) {
					for (int j = 0; j < seg; j++) {
						quad(vc, P, prev[j], prev[j + 1], ring[j + 1], ring[j], prevC[j], prevC[j + 1], cols[j + 1], cols[j]);
					}
				}
				prev = ring;
				prevC = cols;
			}
			// a faint outer glow and the bright point
			pose.pushPose();
			pose.translate(0f, -len, 0f);
			TurboDraw.box(vc, pose.last(), 0.06f + r * 0.05f, 0.06f + r * 0.05f, 0.06f + r * 0.05f, 0xFFFFFF, 0.95f * alpha);
			pose.popPose();
			// the base: a ring of an armoured collar with glowing teeth (it turns with the drill)
			int collar = argb(HardLightDraw.tint(GREEN), 0.75f * alpha), collarHi = argb(HardLightDraw.tint(PALE), 0.95f * alpha);
			float cr = r * 1.14f, ch = 0.22f + r * 0.06f;
			for (int j = 0; j < seg; j++) {
				double a0 = j * Math.PI * 2 / seg, a1 = (j + 1) * Math.PI * 2 / seg;
				float x0 = (float) Math.cos(a0) * cr, z0 = (float) Math.sin(a0) * cr, x1 = (float) Math.cos(a1) * cr, z1 = (float) Math.sin(a1) * cr;
				quad(vc, P, p(x0, ch, z0), p(x1, ch, z1), p(x1, -ch * 0.4f, z1), p(x0, -ch * 0.4f, z0), collarHi, collarHi, collar, collar);
				quad(vc, P, p(x0, ch, z0), p(x0 * 0.55f, ch, z0 * 0.55f), p(x1 * 0.55f, ch, z1 * 0.55f), p(x1, ch, z1), collar);
			}
			for (int t = 0; t < 8; t++) {
				pose.pushPose();
				pose.mulPose(Axis.YP.rotation(spin * 0.33f + t * (float) Math.PI / 4f));
				pose.translate(cr, ch * 0.3f, 0f);
				TurboDraw.box(vc, pose.last(), 0.05f, ch * 0.6f, 0.07f, 0xF0FFF4, 0.9f * alpha);
				pose.popPose();
			}
		} finally {
			HardLightDraw.golden = false;
		}
	}

	// ================================================================ iaido crescent

	/**
	 * The crescent of light, flying along +Z from the origin (its front at the origin), {@code halfW} blocks either side:
	 * thick and bright in the middle, tapering to points at the tips, a white core along its leading edge.
	 */
	public static void crescent(VertexConsumer vc, PoseStack pose, float halfW, float alpha, float age, boolean golden) {
		HardLightDraw.golden = golden;
		try {
			PoseStack.Pose P = pose.last();
			int n = 30;
			float maxA = (float) Math.toRadians(70);
			float R = halfW / (float) Math.sin(maxA);
			for (int layer = 0; layer < 3; layer++) {
				float grow = 1f + layer * 0.22f;
				float la = layer == 0 ? 1f : layer == 1 ? 0.4f : 0.16f;
				int front = argb(layer == 0 ? 0xFFFFFF : HardLightDraw.tint(PALE), 0.95f * alpha * la);
				int back = argb(HardLightDraw.tint(GREEN), 0.04f * alpha * la);
				int midc = argb(HardLightDraw.tint(layer == 0 ? PALE : GREEN), 0.62f * alpha * la);
				float[][] o = new float[n + 1][], up = new float[n + 1][], dn = new float[n + 1][], in = new float[n + 1][];
				for (int i = 0; i <= n; i++) {
					float th = -maxA + 2 * maxA * i / n;
					float k = (float) Math.cos(th / maxA * Math.PI / 2);
					float thick = (1.5f * k + 0.05f) * grow;
					float h = (0.38f * k + 0.02f) * grow;
					float ox = (float) Math.sin(th) * R, oz = (float) Math.cos(th) * R - R;
					// the inner (trailing) arc sits further back in the middle: a crescent, not a band
					float ix = (float) Math.sin(th) * (R - thick * 0.6f), iz = (float) Math.cos(th) * (R - thick * 0.6f) - R - thick * 0.6f;
					o[i] = p(ox, 0f, oz);
					in[i] = p(ix, 0f, iz);
					float mx = ox * 0.6f + ix * 0.4f, mz = oz * 0.6f + iz * 0.4f;
					up[i] = p(mx, h, mz);
					dn[i] = p(mx, -h, mz);
				}
				for (int i = 0; i < n; i++) {
					// upper and lower sheets: the leading edge bright, thickest just behind it, fading to the trailing arc
					quad(vc, P, o[i], o[i + 1], up[i + 1], up[i], front, front, midc, midc);
					quad(vc, P, up[i], up[i + 1], in[i + 1], in[i], midc, midc, back, back);
					quad(vc, P, o[i], dn[i], dn[i + 1], o[i + 1], front, midc, midc, front);
					quad(vc, P, dn[i], in[i], in[i + 1], dn[i + 1], midc, back, back, midc);
				}
			}
		} finally {
			HardLightDraw.golden = false;
		}
	}

	/** A ring of light {@code r} across in the XY plane (facing +Z), {@code w} thick. */
	public static void ring(VertexConsumer vc, PoseStack pose, float r, float w, int rgb, float alpha) {
		PoseStack.Pose P = pose.last();
		int n = 40;
		int c = argb(rgb, alpha), ci = argb(rgb, 0f);
		for (int i = 0; i < n; i++) {
			double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			quad(vc, P, p(c0 * (r - w), s0 * (r - w), 0), p(c1 * (r - w), s1 * (r - w), 0), p(c1 * r, s1 * r, 0), p(c0 * r, s0 * r, 0), ci, ci, c, c);
			quad(vc, P, p(c0 * r, s0 * r, 0), p(c1 * r, s1 * r, 0), p(c1 * (r + w), s1 * (r + w), 0), p(c0 * (r + w), s0 * (r + w), 0), c, c, ci, ci);
		}
	}
}
