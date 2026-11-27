package com.projecthero.mod.client.blackpanther;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * v0.17.7 (user: "make the cape build itself pixel by pixel aswell please like how it does for thor"): the Black Panther's 3D cape
 * ({@link BlackPantherCapeLayer}) and high collar ({@link BlackPantherCollarLayer}) build texel by texel the way Thor's cape does
 * ({@code ThorSuitReveal#capeTexture}): pre-built copies of the cloth texture with the not-yet-formed texels cleared (the cloth draws
 * cut-out, so a cleared texel is simply not there), swapped in as the build runs, the newest texels flashing the suit's violet edge.
 * The cloth hangs at full length the whole time -- it no longer unrolls; its pixels fill in.
 * <ul>
 *   <li><b>Cape</b>: from the collar down to the hem (v), with per-texel scatter so the edge is single pixels popping in.</li>
 *   <li><b>Collar</b>: from its two open front ends (over the shoulders, where the spread from the necklace arrives first) round to
 *       the back of the neck, and from the yoke up to the gold rim.</li>
 * </ul>
 * The progress comes from {@link BlackPantherRender#capeProgress} / {@link BlackPantherRender#collarProgress} (the body build's cover,
 * so it runs in step with the spread reaching the shoulders and back; backwards on retract). Frames are built lazily on the render
 * thread and kept for the session (texture ids only: nothing here holds a world or an entity).
 */
public final class BlackPantherClothReveal {
	/** Reveal steps per cloth. */
	private static final int STEPS = 24;
	/** Share of a texel's order that is its place on the cloth (the rest is scatter). */
	private static final float ORDER_BODY = 0.78f;
	/** The newest texels: hot violet, then a paler violet trail (ARGB). */
	private static final int EDGE_ARGB = 0xFFD08CFF;
	private static final int TRAIL_ARGB = 0xFF9A4CFF;

	private static final Map<String, ResourceLocation[]> FRAMES = new HashMap<>();

	private BlackPantherClothReveal() {
	}

	/** The cape texture at build progress {@code p} (0..1): the shipped texture once complete. */
	public static ResourceLocation cape(ResourceLocation base, float p) {
		if (p >= 0.999f) {
			return base;
		}
		ResourceLocation[] frames = FRAMES.computeIfAbsent("cape", k -> build(base, "bp_cape", (x, y, w, h) -> (y + 0.5f) / h));
		return pick(frames, p, base);
	}

	/** The collar texture at build progress {@code p} (0..1). */
	public static ResourceLocation collar(ResourceLocation base, float p) {
		if (p >= 0.999f) {
			return base;
		}
		ResourceLocation[] frames = FRAMES.computeIfAbsent("collar", k -> build(base, "bp_collar", BlackPantherClothReveal::collarOrder));
		return pick(frames, p, base);
	}

	/**
	 * The collar texture's bands ({@link BlackPantherCollarLayer}): u runs round the neck (0 and 1 the open front ends, 0.5 straight
	 * behind); v: outside wall (top at 0), inside wall, the gold rim, the yoke. Front ends first, the back last; low before high.
	 */
	private static float collarOrder(int x, int y, int w, int h) {
		float u = (x + 0.5f) / w, v = (y + 0.5f) / h;
		float round = 1f - 2f * Math.abs(u - 0.5f); // 0 at the front ends, 1 behind
		float height;
		if (v < 12f / 32f) {
			height = 1f - v / (12f / 32f);
		} else if (v < 24f / 32f) {
			height = 1f - (v - 12f / 32f) / (12f / 32f);
		} else if (v < 28f / 32f) {
			height = 1f; // the rim
		} else {
			height = 0f; // the yoke
		}
		return 0.65f * round + 0.35f * height;
	}

	private interface Order {
		float at(int x, int y, int w, int h);
	}

	private static ResourceLocation pick(ResourceLocation[] frames, float p, ResourceLocation base) {
		if (frames.length == 0) {
			return base;
		}
		return frames[Math.max(0, Math.min(frames.length - 1, (int) Math.floor(p * STEPS)))];
	}

	private static ResourceLocation[] build(ResourceLocation base, String tag, Order order) {
		NativeImage src = read(base);
		if (src == null) {
			return new ResourceLocation[0];
		}
		int w = src.getWidth(), h = src.getHeight();
		int[] step = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int i = y * w + x;
				float o = ORDER_BODY * order.at(x, y, w, h) + (1f - ORDER_BODY) * scatter(i);
				step[i] = Math.max(0, Math.min(STEPS - 1, (int) Math.floor(o * STEPS)));
			}
		}
		Minecraft mc = Minecraft.getInstance();
		ResourceLocation[] out = new ResourceLocation[STEPS + 1];
		for (int k = 0; k <= STEPS; k++) {
			NativeImage img = new NativeImage(w, h, true);
			img.copyFrom(src);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int s = step[y * w + x];
					int rgba = src.getPixelRGBA(x, y);
					if (s >= k) {
						img.setPixelRGBA(x, y, 0);
					} else if (((rgba >>> 24) & 0xFF) != 0 && k < STEPS) {
						if (s == k - 1) {
							img.setPixelRGBA(x, y, abgr(EDGE_ARGB));
						} else if (s == k - 2) {
							img.setPixelRGBA(x, y, blend(rgba, abgr(TRAIL_ARGB), 0.5f));
						}
					}
				}
			}
			ResourceLocation id = ProjectHeroMod.id("dynamic/black_panther/" + tag + "_" + k);
			mc.getTextureManager().register(id, new DynamicTexture(img));
			out[k] = id;
		}
		src.close();
		return out;
	}

	private static NativeImage read(ResourceLocation tex) {
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(tex);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream in = res.get().open()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[black_panther] could not build the cloth build frames for {}: {}", tex, e.toString());
			return null;
		}
	}

	/** A fixed 0..1 scatter per texel. */
	private static float scatter(int texel) {
		long x = texel * 0x9E3779B97F4A7C15L + 0x5BD1E995L;
		x ^= x >>> 31;
		x *= 0xBF58476D1CE4E5B9L;
		x ^= x >>> 29;
		return (x >>> 40) / (float) (1L << 24);
	}

	private static int abgr(int argb) {
		int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	private static int blend(int a, int b, float t) {
		int out = a & 0xFF000000;
		for (int shift = 0; shift < 24; shift += 8) {
			int ca = (a >> shift) & 0xFF, cb = (b >> shift) & 0xFF;
			out |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return out;
	}
}
