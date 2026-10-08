package com.projecthero.mod.client.symbiote;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.projecthero.mod.ProjectHeroMod;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * v0.15.15: the Symbiote suit spreads over its host pixel by pixel <em>outward from the chest</em>, the way the user
 * asked for it -- over the torso, down the arms and legs, and the head last of all (its texels only start once the rest
 * of the body is nearly covered). Suit-down runs the same frames backwards: the head goes first and the chest last.
 *
 * <p>Same technique and look as {@link SymbioteDissolve} (pre-built copies of the suit texture with some pixels cleared,
 * swapped in by {@code SuperheroArmorRenderer#getRenderType}; no extra effects), only the order differs. Every texel is
 * placed on the body through the standard player-skin box-UV layout the three Symbiote suits use (both layers), and
 * ranked by its distance from the middle of the chest <em>along the body</em>: straight across the torso, via the
 * shoulder down an arm, via the hip down a leg, via the neck up the head. A little per-texel jitter keeps the edge
 * ragged and organic. {@code SymbioteDissolve} itself is untouched -- Moon Knight still uses it.
 *
 * <p>Built lazily on the render thread, once per suit texture, and kept for the session.
 */
public final class SymbioteSpread {
	public static final int STEPS = 48;
	/** The body (everything but the head) fills keys 0 .. BODY_SPAN (plus jitter); the head fills HEAD_START .. 1. */
	static final float BODY_SPAN = 0.80f;
	static final float BODY_JITTER = 0.10f;
	static final float HEAD_START = 0.86f;
	static final float HEAD_SPAN = 0.09f;
	static final float HEAD_JITTER = 0.05f;

	/** Middle of the chest, model pixels (feet at y = 0, the neck at y = 24). */
	private static final float[] CHEST = { 0f, 19f, 0f };
	private static final float[] NECK = { 0f, 24f, 0f };
	private static final float[] R_SHOULDER = { -5f, 22f, 0f };
	private static final float[] L_SHOULDER = { 5f, 22f, 0f };
	private static final float[] R_HIP = { -2f, 12f, 0f };
	private static final float[] L_HIP = { 2f, 12f, 0f };

	private static final int HEAD = 0;
	private static final int BODY = 1;
	private static final int R_ARM = 2;
	private static final int L_ARM = 3;
	private static final int R_LEG = 4;
	private static final int L_LEG = 5;

	/** {part, u, v} for every box of the 64x64 player-skin layout, base and overlay layers. */
	private static final int[][] BOXES = {
			{ HEAD, 0, 0 }, { HEAD, 32, 0 },
			{ BODY, 16, 16 }, { BODY, 16, 32 },
			{ R_ARM, 40, 16 }, { R_ARM, 40, 32 },
			{ L_ARM, 32, 48 }, { L_ARM, 48, 48 },
			{ R_LEG, 0, 16 }, { R_LEG, 0, 32 },
			{ L_LEG, 16, 48 }, { L_LEG, 0, 48 },
	};
	/** Per part: box origin (x, y, z) and size (x, y, z), model pixels. */
	private static final float[][] GEOMETRY = {
			{ -4, 24, -4, 8, 8, 8 },
			{ -4, 12, -2, 8, 12, 4 },
			{ -8, 12, -2, 4, 12, 4 },
			{ 4, 12, -2, 4, 12, 4 },
			{ -4, 0, -2, 4, 12, 4 },
			{ 0, 0, -2, 4, 12, 4 },
	};

	private static final Map<ResourceLocation, ResourceLocation[]> CACHE = new HashMap<>();

	private SymbioteSpread() {
	}

	/** The texture to draw for a suit that is {@code progress} (0..1) of the way on. */
	public static ResourceLocation texture(ResourceLocation base, float progress) {
		if (progress >= 0.999f) {
			return base;
		}
		ResourceLocation[] frames = CACHE.computeIfAbsent(base, SymbioteSpread::build);
		if (frames == null || frames.length == 0) {
			return base;
		}
		int step = Math.max(0, Math.min(STEPS - 1, (int) Math.floor(progress * STEPS)));
		return frames[step];
	}

	/** Is {@code armorSetId} one of the three Symbiote suits? */
	public static boolean isSymbioteSet(String armorSetId) {
		return "symbiote_host".equals(armorSetId) || "spider_man_symbiote".equals(armorSetId) || "agent_venom".equals(armorSetId);
	}

	/** The first-person sleeve's texture for the local player: spreading with the suit while it is coming on / off. */
	public static ResourceLocation firstPerson(String armorSetId, ResourceLocation texture) {
		net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !isSymbioteSet(armorSetId) || !SymbioteReveal.isRevealing(player)) {
			return texture;
		}
		return texture(texture, SymbioteReveal.progress(player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)));
	}

	private static ResourceLocation[] build(ResourceLocation base) {
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> res = mc.getResourceManager().getResource(base);
		if (res.isEmpty()) {
			return new ResourceLocation[0];
		}
		NativeImage src;
		try (InputStream in = res.get().open()) {
			src = NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not build the Symbiote spread for {}: {}", base, e.toString());
			return new ResourceLocation[0];
		}
		int w = src.getWidth();
		int h = src.getHeight();
		float[] key = keys(w, h, base.hashCode() * 31L + 1515L);
		String tag = base.getNamespace() + "_" + base.getPath().replaceAll("[^a-z0-9_]", "_");
		ResourceLocation[] frames = new ResourceLocation[STEPS];
		for (int k = 0; k < STEPS; k++) {
			float shown = k / (float) STEPS;
			NativeImage img = new NativeImage(w, h, true);
			img.copyFrom(src);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					float kv = key[y * w + x];
					if (!Float.isNaN(kv) && kv >= shown) {
						img.setPixelRGBA(x, y, 0);
					}
				}
			}
			ResourceLocation id = ProjectHeroMod.id("dynamic/symbiote_spread/" + tag + "_" + k);
			mc.getTextureManager().register(id, new DynamicTexture(img));
			frames[k] = id;
		}
		src.close();
		return frames;
	}

	/**
	 * Every texel's reveal key in 0..1 (NaN = not on any box, left alone): the order the suit covers it in. Pure maths,
	 * no rendering -- {@code w}/{@code h} may be an HD multiple of 64.
	 */
	static float[] keys(int w, int h, long seed) {
		float[] dist = new float[w * h];
		int[] part = new int[w * h];
		java.util.Arrays.fill(dist, Float.NaN);
		java.util.Arrays.fill(part, -1);
		float su = w / 64f;
		float sv = h / 64f;
		for (int[] box : BOXES) {
			int p = box[0];
			float[] g = GEOMETRY[p];
			float sx = g[3], sy = g[4], sz = g[5];
			float u = box[1], v = box[2];
			// box-UV faces: {u0, v0, du, dv, face}, where face picks how (fu, fv) land on the box
			float[][] faces = {
					{ u + sz, v, sx, sz, 0 },                 // top
					{ u + sz + sx, v, sx, sz, 1 },            // bottom
					{ u, v + sz, sz, sy, 2 },                 // right side (-x)
					{ u + sz, v + sz, sx, sy, 3 },            // front (-z)
					{ u + sz + sx, v + sz, sz, sy, 4 },       // left side (+x)
					{ u + 2 * sz + sx, v + sz, sx, sy, 5 },   // back (+z)
			};
			for (float[] f : faces) {
				int x0 = (int) Math.floor(f[0] * su);
				int x1 = (int) Math.ceil((f[0] + f[2]) * su);
				int y0 = (int) Math.floor(f[1] * sv);
				int y1 = (int) Math.ceil((f[1] + f[3]) * sv);
				for (int ty = y0; ty < y1; ty++) {
					for (int tx = x0; tx < x1; tx++) {
						if (tx < 0 || ty < 0 || tx >= w || ty >= h) {
							continue;
						}
						float fu = ((tx + 0.5f) / su - f[0]) / f[2];
						float fv = ((ty + 0.5f) / sv - f[1]) / f[3];
						float[] pt = point((int) f[4], fu, fv, g);
						int i = ty * w + tx;
						dist[i] = pathDistance(p, pt);
						part[i] = p;
					}
				}
			}
		}
		float maxBody = 1e-3f;
		float maxHead = 1e-3f;
		for (int i = 0; i < dist.length; i++) {
			if (part[i] < 0) {
				continue;
			}
			if (part[i] == HEAD) {
				maxHead = Math.max(maxHead, dist[i]);
			} else {
				maxBody = Math.max(maxBody, dist[i]);
			}
		}
		java.util.Random rng = new java.util.Random(seed);
		float[] key = new float[w * h];
		java.util.Arrays.fill(key, Float.NaN);
		float top = 0f;
		for (int i = 0; i < key.length; i++) {
			if (part[i] < 0) {
				continue;
			}
			key[i] = part[i] == HEAD
					? HEAD_START + HEAD_SPAN * (dist[i] / maxHead) + HEAD_JITTER * rng.nextFloat()
					: BODY_SPAN * (dist[i] / maxBody) + BODY_JITTER * rng.nextFloat();
			top = Math.max(top, key[i]);
		}
		// normalise so the very last texel lands at the end of the clock
		for (int i = 0; i < key.length; i++) {
			if (!Float.isNaN(key[i])) {
				key[i] = Math.min(0.9999f, key[i] / top);
			}
		}
		return key;
	}

	/** Where face texel (fu, fv) sits on a box of geometry {@code g}, model pixels. */
	private static float[] point(int face, float fu, float fv, float[] g) {
		float ox = g[0], oy = g[1], oz = g[2], sx = g[3], sy = g[4], sz = g[5];
		fu = Math.max(0f, Math.min(1f, fu));
		fv = Math.max(0f, Math.min(1f, fv));
		return switch (face) {
			case 0 -> new float[] { ox + sx * fu, oy + sy, oz + sz * fv };            // top
			case 1 -> new float[] { ox + sx * fu, oy, oz + sz * fv };                 // bottom
			case 2 -> new float[] { ox, oy + sy * (1 - fv), oz + sz * fu };          // -x side
			case 3 -> new float[] { ox + sx * fu, oy + sy * (1 - fv), oz };          // front
			case 4 -> new float[] { ox + sx, oy + sy * (1 - fv), oz + sz * (1 - fu) }; // +x side
			default -> new float[] { ox + sx * (1 - fu), oy + sy * (1 - fv), oz + sz }; // back
		};
	}

	/** Distance from the middle of the chest to {@code pt}, travelling along the body through the right joint. */
	private static float pathDistance(int part, float[] pt) {
		return switch (part) {
			case BODY -> d(CHEST, pt);
			case HEAD -> d(NECK, pt); // the head is ranked on its own, after the body
			case R_ARM -> d(CHEST, R_SHOULDER) + d(R_SHOULDER, pt);
			case L_ARM -> d(CHEST, L_SHOULDER) + d(L_SHOULDER, pt);
			case R_LEG -> d(CHEST, R_HIP) + d(R_HIP, pt);
			default -> d(CHEST, L_HIP) + d(L_HIP, pt);
		};
	}

	private static float d(float[] a, float[] b) {
		float x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
		return (float) Math.sqrt(x * x + y * y + z * z);
	}

	/** Test hook: the reveal key of the texel at skin coordinate (u, v) in a 64x64 layout (NaN if unused). */
	public static float keyAt(int u, int v) {
		return keys(64, 64, 0L)[v * 64 + u];
	}
}
