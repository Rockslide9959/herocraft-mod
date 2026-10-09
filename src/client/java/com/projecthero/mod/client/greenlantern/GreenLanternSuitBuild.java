package com.projecthero.mod.client.greenlantern;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.joml.Vector3f;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.ArmorSweepReveal;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * v0.15.18, explicit user request ("the suit has to build outwards onto the body starting from the ring"): the Green Lantern
 * suit grows over the wearer texel by texel, like Max Steel's nanotech reveal, starting at the Power Ring on the right fist.
 *
 * <p>Every texel of the suit gets an <em>order</em>: how far it is from the ring measured <b>along the body</b>, not in a
 * straight line through space -- ring -&gt; up the ring arm -&gt; right shoulder, then across the chest, down through the hips
 * into each leg, up the neck over the head, and over the far shoulder down the other arm. (The 0.15.15 sweep measured a
 * straight-line sphere out of the shoulder, which reached the whole chest, the head and the far arm within a couple of
 * frames, and quantised it to one-pixel rows, so in game the suit looked like it simply faded on everywhere at once.) A
 * little per-texel jitter breaks the front up into a crawling nanite edge. The texels right at the front are painted a
 * white-hot green fading back into Lantern green over a short band, so a bright forming edge visibly travels from the ring
 * outward. Suit-down plays the same frames backwards: the suit recedes into the ring.
 *
 * <p>Works on every {@link com.projecthero.mod.greenlantern.GreenLanternSuitStyle} (they all share the 64x64 player-skin UV
 * layout of {@code geo/green_lantern.geo.json} -- the slim rig of the skin-based suits uses the same UVs), so third person,
 * other viewers, the first-person sleeve ({@code SuperheroFirstPersonArm}) and the first-person body all show it. Frames are
 * built lazily, {@link #STEPS} + 1 per suit texture, and kept for the session.
 */
public final class GreenLanternSuitBuild {
	/** Frames over the whole build: finer than the 30-tick suit-up's 20 tps, so the edge moves every frame-ish. */
	public static final int STEPS = 72;
	/** Width of the glowing forming edge, as a fraction of the whole build. */
	private static final float EDGE = 0.085f;
	/** White-hot front and Lantern-green trail (ARGB). */
	private static final int FRONT_ARGB = 0xFFEAFFEF;
	private static final int TRAIL_ARGB = 0xFF4DFF85;
	/** How much (model px) the nanite jitter can delay a texel. */
	private static final float JITTER = 1.6f;

	// ---- the body path, in the geometry's own model units (y up, the right arm on -x; the ring hand is the right one,
	// see PowerRingLayer / HandRing: band at y = 8.5 down the arm from the shoulder pivot (22) on the knuckles)
	private static final Vector3f RING = new Vector3f(-5.5f, 13.5f, -1.5f);
	private static final Vector3f R_SHOULDER = new Vector3f(-5.0f, 22.5f, 0.0f);
	private static final Vector3f L_SHOULDER = new Vector3f(5.0f, 22.5f, 0.0f);
	private static final Vector3f NECK = new Vector3f(0.0f, 24.0f, 0.0f);
	private static final Vector3f R_HIP = new Vector3f(-1.9f, 12.0f, 0.0f);
	private static final Vector3f L_HIP = new Vector3f(1.9f, 12.0f, 0.0f);

	private static final Map<ResourceLocation, Frames> CACHE = new HashMap<>();

	private static final class Frames {
		final NativeImage src;
		final float[] order; // 0..1, NaN = not on the model
		final ResourceLocation[] ids = new ResourceLocation[STEPS + 1];
		final String tag;

		Frames(NativeImage src, float[] order, String tag) {
			this.src = src;
			this.order = order;
			this.tag = tag;
		}
	}

	private GreenLanternSuitBuild() {
	}

	/**
	 * The texture to draw a suit that is {@code progress} (0..1) built, {@code base} at 1 (or if anything could not be
	 * read). {@code geometry} is the suit rig whose UVs the texture follows.
	 */
	public static ResourceLocation texture(ResourceLocation geometry, ResourceLocation base, float progress) {
		if (progress >= 0.999f) {
			return base;
		}
		Frames f = CACHE.computeIfAbsent(base, b -> build(geometry, b));
		if (f == null) {
			return base;
		}
		int step = Math.max(0, Math.min(STEPS, Math.round(progress * STEPS)));
		if (f.ids[step] == null) {
			f.ids[step] = paint(f, step);
		}
		return f.ids[step];
	}

	/** How far along the body (model px) a point on {@code rootBone} is from the ring. */
	static float bodyDistance(String rootBone, Vector3f p) {
		float toShoulder = RING.distance(R_SHOULDER);
		switch (rootBone) {
			case "armorRightArm":
				return p.distance(RING);
			case "armorBody":
				return toShoulder + p.distance(R_SHOULDER);
			case "armorHead":
				return toShoulder + R_SHOULDER.distance(NECK) + p.distance(NECK);
			case "armorLeftArm":
				return toShoulder + R_SHOULDER.distance(L_SHOULDER) + p.distance(L_SHOULDER);
			case "armorRightLeg":
			case "armorRightBoot":
				return toShoulder + R_SHOULDER.distance(R_HIP) + p.distance(R_HIP);
			case "armorLeftLeg":
			case "armorLeftBoot":
				return toShoulder + R_SHOULDER.distance(L_HIP) + p.distance(L_HIP);
			default:
				return toShoulder + p.distance(R_SHOULDER);
		}
	}

	private static Frames build(ResourceLocation geometry, ResourceLocation base) {
		NativeImage src = read(base);
		if (src == null) {
			return null;
		}
		int w = src.getWidth();
		int h = src.getHeight();
		float[] dist = new float[w * h];
		java.util.Arrays.fill(dist, Float.NaN);
		boolean ok = ArmorSweepReveal.forEachTexel(geometry, w, h, (i, bone, p) -> {
			// deterministic per-texel jitter so the front crawls instead of advancing as a clean line
			int hsh = i * 0x9E3779B1;
			hsh ^= hsh >>> 15;
			hsh *= 0x85EBCA6B;
			hsh ^= hsh >>> 13;
			float d = bodyDistance(bone, p) + JITTER * ((hsh >>> 8) & 0xFFFF) / 65535f;
			// a texel shared by several faces forms with the earliest of them
			if (Float.isNaN(dist[i]) || d < dist[i]) {
				dist[i] = d;
			}
		});
		float max = 0f;
		for (float d : dist) {
			if (!Float.isNaN(d)) {
				max = Math.max(max, d);
			}
		}
		if (!ok || max <= 0f) {
			src.close();
			return null;
		}
		for (int i = 0; i < dist.length; i++) {
			dist[i] = Float.isNaN(dist[i]) ? Float.NaN : dist[i] / max;
		}
		String tag = base.getPath().replaceAll("[^a-z0-9_]", "_");
		return new Frames(src, dist, tag);
	}

	private static ResourceLocation paint(Frames f, int step) {
		NativeImage src = f.src;
		int w = src.getWidth();
		int h = src.getHeight();
		NativeImage img = new NativeImage(w, h, true);
		img.copyFrom(src);
		// the front runs a little past 1 so the last texels finish glowing and settle before the clock ends
		float front = step / (float) STEPS * (1.0f + EDGE);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float o = f.order[y * w + x];
				if (Float.isNaN(o)) {
					continue; // not on the model: nothing draws it
				}
				int px = src.getPixelRGBA(x, y);
				if (o > front) {
					img.setPixelRGBA(x, y, 0);
				} else if (front - o < EDGE && ((px >>> 24) & 0xFF) != 0) {
					// 0 at the very front (white-hot) .. 1 at the back of the band (the suit's own colour)
					float t = (front - o) / EDGE;
					int edge = t < 0.45f ? lerpArgb(FRONT_ARGB, TRAIL_ARGB, t / 0.45f)
							: lerpArgb(TRAIL_ARGB, abgrToArgb(px) | 0xFF000000, (t - 0.45f) / 0.55f);
					img.setPixelRGBA(x, y, argbToAbgr(edge));
				}
			}
		}
		ResourceLocation id = ProjectHeroMod.id("dynamic/gl_suit_build/" + f.tag + "_" + step);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
		return id;
	}

	private static int lerpArgb(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	/** NativeImage pixels are ABGR. */
	private static int argbToAbgr(int argb) {
		return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
	}

	private static int abgrToArgb(int abgr) {
		return (abgr & 0xFF00FF00) | ((abgr >> 16) & 0xFF) | ((abgr & 0xFF) << 16);
	}

	private static NativeImage read(ResourceLocation id) {
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream in = res.get().open()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not read {} for the Green Lantern suit build: {}", id, e.toString());
			return null;
		}
	}
}
