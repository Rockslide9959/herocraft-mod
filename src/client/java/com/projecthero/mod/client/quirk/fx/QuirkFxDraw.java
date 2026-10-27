package com.projecthero.mod.client.quirk.fx;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1 Quirk framework: the drawing primitives of the VFX toolkit (units are blocks, positions relative to the pose).
 * Two looks:
 * <ul>
 * <li><b>glow</b> -- additive and never writing depth ({@link #glow}): fire, light, sparks, flashes. Colours are
 * premultiplied by their alpha here, so "fading" is just a lower alpha;</li>
 * <li><b>matter</b> -- lit translucent ({@link #matter}): smoke, decals, cloth, shadow, ink.</li>
 * </ul>
 * A kit's own client code can use these directly for one-off geometry.
 */
public final class QuirkFxDraw {
	public static final int FULL_BRIGHT = 0xF000F0;
	public static final ResourceLocation GLOW = tex("glow");
	public static final ResourceLocation WHITE = tex("white");
	public static final ResourceLocation SMOKE = tex("smoke");
	public static final ResourceLocation RIBBON = tex("ribbon");
	public static final ResourceLocation BOOK_FIREBALL = tex("book_fireball");
	public static final ResourceLocation BOOK_SMOKE = tex("book_smoke");
	public static final ResourceLocation BOOK_INK = tex("book_ink");
	public static final ResourceLocation[] DECALS = { tex("decal_scorch"), tex("decal_crack"), tex("decal_pool"), tex("decal_print") };

	private QuirkFxDraw() {
	}

	private static ResourceLocation tex(String name) {
		return ProjectHeroMod.id("textures/entity/quirk/fx/" + name + ".png");
	}

	/**
	 * Additive, no depth write (the eyes buffer). Fetch a buffer right before drawing into it: asking the buffer source for
	 * another render type ends the earlier batch, and a consumer kept from before throws "Not building!".
	 */
	public static VertexConsumer glow(MultiBufferSource buffers, ResourceLocation texture) {
		return buffers.getBuffer(RenderType.eyes(texture));
	}

	/** Lit translucent, double-sided. */
	public static VertexConsumer matter(MultiBufferSource buffers, ResourceLocation texture) {
		return buffers.getBuffer(RenderType.entityTranslucent(texture));
	}

	public static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int r, int g, int b, int a,
			int light, float nx, float ny, float nz) {
		vc.addVertex(pose, x, y, z).setColor(Mth.clamp(r, 0, 255), Mth.clamp(g, 0, 255), Mth.clamp(b, 0, 255), Mth.clamp(a, 0, 255)).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}

	/** A glow vertex: rgb premultiplied by {@code alpha} (0..1). */
	public static void gv(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int rgb, float alpha) {
		float a = Mth.clamp(alpha, 0f, 1f);
		vertex(vc, pose, x, y, z, u, v, (int) (((rgb >> 16) & 255) * a), (int) (((rgb >> 8) & 255) * a), (int) ((rgb & 255) * a), (int) (a * 255),
				FULL_BRIGHT, 0, 1, 0);
	}

	/** A matter vertex: rgb with alpha, lit by {@code light}. */
	public static void mv(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int rgb, float alpha, int light,
			float nx, float ny, float nz) {
		vertex(vc, pose, x, y, z, u, v, (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, (int) (Mth.clamp(alpha, 0f, 1f) * 255), light, nx, ny, nz);
	}

	/**
	 * A double-sided glow quad (the additive buffer culls back faces): corners {@code p} (x, y, z, u, v) x 4 with their own
	 * colour and alpha.
	 */
	public static void gquad(VertexConsumer vc, PoseStack.Pose pose, float[][] p, int[] rgb, float[] a) {
		for (int i = 0; i < 4; i++) {
			gv(vc, pose, p[i][0], p[i][1], p[i][2], p[i][3], p[i][4], rgb[i], a[i]);
		}
		for (int i = 3; i >= 0; i--) {
			gv(vc, pose, p[i][0], p[i][1], p[i][2], p[i][3], p[i][4], rgb[i], a[i]);
		}
	}

	/** Colour between {@code a} and {@code b} (RGB). */
	public static int lerpRgb(float t, int a, int b) {
		t = Mth.clamp(t, 0f, 1f);
		int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
		int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
		int bl = (int) Mth.lerp(t, a & 255, b & 255);
		return (r << 16) | (g << 8) | bl;
	}

	/** A camera-facing square of half-size {@code h} at {@code c}, rotated {@code roll} about the view axis; UV sub-rect u0..u1. */
	public static void billboard(VertexConsumer vc, PoseStack.Pose pose, Quaternionf cam, Vector3f c, float h, float roll, float u0, float u1,
			int rgb, float alpha, boolean glow, int light) {
		Vector3f right = cam.transform(new Vector3f(Mth.cos(roll), Mth.sin(roll), 0)).mul(h);
		Vector3f up = cam.transform(new Vector3f(-Mth.sin(roll), Mth.cos(roll), 0)).mul(h);
		Vector3f n = cam.transform(new Vector3f(0, 0, 1));
		float[][] q = { { -1, -1, u0, 1 }, { 1, -1, u1, 1 }, { 1, 1, u1, 0 }, { -1, 1, u0, 0 } };
		float[][] p = new float[4][];
		for (int i = 0; i < 4; i++) {
			float[] k = q[i];
			p[i] = new float[] { c.x + right.x * k[0] + up.x * k[1], c.y + right.y * k[0] + up.y * k[1], c.z + right.z * k[0] + up.z * k[1], k[2], k[3] };
		}
		if (glow) {
			gquad(vc, pose, p, new int[] { rgb, rgb, rgb, rgb }, new float[] { alpha, alpha, alpha, alpha });
		} else {
			for (float[] k : p) {
				mv(vc, pose, k[0], k[1], k[2], k[3], k[4], rgb, alpha, light, n.x, n.y, n.z);
			}
		}
	}

	/** A flat glowing ring in the XZ plane between {@code rIn} and {@code rOut}; inner alpha {@code aIn}, outer {@code aOut}. */
	public static void ring(VertexConsumer vc, PoseStack.Pose pose, float rIn, float rOut, int segs, int rgbIn, float aIn, int rgbOut, float aOut) {
		for (int s = 0; s < segs; s++) {
			float t0 = Mth.TWO_PI * s / segs, t1 = Mth.TWO_PI * (s + 1) / segs;
			float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
			gquad(vc, pose, new float[][] { { c0 * rIn, 0, s0 * rIn, 0.5f, 0.5f }, { c0 * rOut, 0, s0 * rOut, 0.5f, 0.5f },
					{ c1 * rOut, 0, s1 * rOut, 0.5f, 0.5f }, { c1 * rIn, 0, s1 * rIn, 0.5f, 0.5f } },
					new int[] { rgbIn, rgbOut, rgbOut, rgbIn }, new float[] { aIn, aOut, aOut, aIn });
		}
	}

	/**
	 * A glowing lat-long sphere shell of radius {@code r}, each vertex pushed out by up to {@code wobble} x r of smooth noise
	 * (seeded) -- the fire-ball look. Colour {@code rgb}, alpha {@code a}; the rim (grazing view) is drawn at {@code rimA} of it.
	 */
	public static void shell(VertexConsumer vc, PoseStack.Pose pose, float r, float wobble, long seed, float phase, int rgb, float a) {
		int lat = 10, lon = 16;
		for (int i = 0; i < lat; i++) {
			float v0 = Mth.PI * i / lat, v1 = Mth.PI * (i + 1) / lat;
			for (int j = 0; j < lon; j++) {
				float u0 = Mth.TWO_PI * j / lon, u1 = Mth.TWO_PI * (j + 1) / lon;
				float[][] p = { shellV(r, wobble, seed, phase, v0, u0, i, j, lon), shellV(r, wobble, seed, phase, v1, u0, i + 1, j, lon),
						shellV(r, wobble, seed, phase, v1, u1, i + 1, j + 1, lon), shellV(r, wobble, seed, phase, v0, u1, i, j + 1, lon) };
				gquad(vc, pose, p, new int[] { rgb, rgb, rgb, rgb }, new float[] { a * p[0][5], a * p[1][5], a * p[2][5], a * p[3][5] });
			}
		}
	}

	/** One shell vertex: x, y, z, u, v, brightness. */
	private static float[] shellV(float r, float wobble, long seed, float phase, float v, float u, int i, int j, int lon) {
		float sx = Mth.sin(v) * Mth.cos(u), sy = Mth.cos(v), sz = Mth.sin(v) * Mth.sin(u);
		float n = noise(seed, i, Mth.sin(v) < 1.0e-3f ? 0 : j % lon, phase); // one value at each pole: no seam
		float rr = r * (1f + wobble * (n - 0.5f) * 2f);
		// the lower half a touch dimmer: the ball reads as lit from inside
		float k = 0.7f + 0.3f * (0.5f + 0.5f * sy) + 0.25f * (n - 0.5f);
		return new float[] { sx * rr, sy * rr, sz * rr, 0.5f, 0.5f, k };
	}

	/** Smooth-ish per-vertex noise, animated by {@code phase}. */
	public static float noise(long seed, int i, int j, float phase) {
		float a = hash(seed, i, j);
		float b = hash(seed + 31, i, j);
		return 0.5f + 0.5f * Mth.sin(phase * (1.2f + b * 2.2f) + a * Mth.TWO_PI);
	}

	public static float hash(long seed, int i, int j) {
		long h = seed * 6364136223846793005L + i * 1442695040888963407L + j * 2862933555777941757L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h & 0xFFFFFF) / (float) 0xFFFFFF;
	}

	/** A small cube of half-size {@code h} (matter look), UVs u0..u1 / v0..v1 on every face (a block sprite). */
	public static void cube(VertexConsumer vc, PoseStack.Pose pose, float h, float u0, float u1, float v0, float v1, int rgb, float alpha, int light) {
		float[][] faces = {
				{ 0, 1, 0 }, { 0, -1, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
		for (float[] n : faces) {
			Vector3f nn = new Vector3f(n[0], n[1], n[2]);
			Vector3f a = Math.abs(nn.y) > 0.5f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
			Vector3f b = new Vector3f(nn).cross(a);
			Vector3f c = new Vector3f(nn).mul(h);
			a.mul(h);
			b.mul(h);
			float[][] q = { { -1, -1, u0, v1 }, { 1, -1, u1, v1 }, { 1, 1, u1, v0 }, { -1, 1, u0, v0 } };
			for (float[] k : q) {
				mv(vc, pose, c.x + a.x * k[0] + b.x * k[1], c.y + a.y * k[0] + b.y * k[1], c.z + a.z * k[0] + b.z * k[1], k[2], k[3], rgb, alpha,
						light, n[0], n[1], n[2]);
			}
		}
	}
}
