package com.projecthero.mod.client.sentry;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * v0.17.5 rework: every texture the Sentry's look needs, and the per-pixel <b>dissolve</b> between two of them.
 *
 * <h2>The looks</h2>
 * <ul>
 *   <li>{@link Look#SUIT}: the comics Sentry suit -- the user's PlanetMinecraft skin ({@code sentry_comics_wide/_slim.png}, made by
 *   {@code scratchpad/sentry/make_suit_textures.js}: face, hair and bare skin cleared so the wearer's own show) when the file is
 *   there, else the mod's own original {@code sentry_suit.png} (whose head was always empty).</li>
 *   <li>{@link Look#VOID_SUIT}: the Sentry merged with the Void, the same way ({@code sentry_void_wide/_slim.png}), else the original
 *   {@code void_form.png} with its head and hat cleared here.</li>
 *   <li>{@link Look#VOID_BODY}: merged with the Void without the suit -- the original Void Form body (head included).</li>
 *   <li>{@link Look#BLACK}: the takeover -- all black, two white pixel eyes ({@code void_body.png}, original).</li>
 * </ul>
 * <b>Held back:</b> the two borrowed skins are not in git ({@code .gitignore}; the comics skin's page says "ask me before using it in a
 * heropack or addon"), so a public build has no {@code sentry_comics_*} / {@code sentry_void_*} files and falls back to the originals
 * on its own (same mechanism as the Gyro suit in v0.17.4: a missing file just isn't used). Each look has an emissive twin
 * ({@code *_glow}); a missing glow draws none.
 *
 * <h2>The dissolve</h2>
 * {@link #step}: 16 in-between textures from look A to look B. Each texel flips at its own threshold -- 75% how far it sits on the body
 * from the chest (the core: the change spreads out from the heart to the hands, feet and head), 25% noise -- so the Void seeps outward
 * over the suit. The texels flipping in a step also get a violet-white fringe in the glow texture (the burning edge). Built lazily, 64x64,
 * released on every resource reload.
 */
public final class SentrySuitTextures {
	public static final int STEPS = 16;

	public enum Look {
		SUIT, VOID_SUIT, VOID_BODY, BLACK
	}

	/** One loaded look: its base and glow images (null: none) for the arm width. */
	private record Pair(@Nullable NativeImage base, @Nullable NativeImage glow, @Nullable ResourceLocation baseId, @Nullable ResourceLocation glowId) {
	}

	private static final Map<String, Optional<Pair>> LOOKS = new HashMap<>();
	private static final Map<String, ResourceLocation[]> STEP_CACHE = new HashMap<>();
	private static final List<ResourceLocation> DYNAMIC = new ArrayList<>();
	private static final List<NativeImage> IMAGES = new ArrayList<>();
	private static float[] order;

	private SentrySuitTextures() {
	}

	static void initialize() {
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return ProjectHeroMod.id("sentry_suit_textures");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				clear();
			}
		});
	}

	static void clear() {
		LOOKS.clear();
		STEP_CACHE.clear();
		for (ResourceLocation id : DYNAMIC) {
			Minecraft.getInstance().getTextureManager().release(id);
		}
		DYNAMIC.clear();
		for (NativeImage img : IMAGES) {
			img.close();
		}
		IMAGES.clear();
	}

	static ResourceLocation tex(String name) {
		return ProjectHeroMod.id("textures/entity/sentry/" + name + ".png");
	}

	/** Whether the borrowed (held-back) comics / void skins are in this build. */
	public static boolean borrowedPresent() {
		return exists(tex("sentry_comics_wide")) && exists(tex("sentry_void_wide"));
	}

	private static boolean exists(ResourceLocation id) {
		return Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
	}

	private static @Nullable NativeImage read(ResourceLocation id) {
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream in = res.get().open()) {
			NativeImage img = NativeImage.read(in);
			if (img.getWidth() != 64 || img.getHeight() != 64) {
				ProjectHeroMod.LOGGER.warn("Sentry texture {} is not 64x64", id);
				img.close();
				return null;
			}
			IMAGES.add(img);
			return img;
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("Sentry texture {} unreadable: {}", id, e.toString());
			return null;
		}
	}

	private static ResourceLocation register(String name, NativeImage img) {
		NativeImage copy = new NativeImage(64, 64, true);
		copy.copyFrom(img);
		ResourceLocation id = Minecraft.getInstance().getTextureManager().register("projecthero_sentry_" + name, new DynamicTexture(copy));
		DYNAMIC.add(id);
		return id;
	}

	private static @Nullable Pair look(Look look, boolean slim) {
		String key = look + (slim ? "_slim" : "_wide");
		Optional<Pair> p = LOOKS.get(key);
		if (p == null) {
			p = Optional.ofNullable(load(look, slim));
			LOOKS.put(key, p);
		}
		return p.orElse(null);
	}

	private static @Nullable Pair load(Look look, boolean slim) {
		String w = slim ? "_slim" : "_wide";
		String old = slim ? "_slim" : "";
		switch (look) {
			case SUIT -> {
				if (exists(tex("sentry_comics" + w))) {
					return pair(tex("sentry_comics" + w), tex("sentry_comics_glow" + w), false);
				}
				return pair(tex("sentry_suit" + old), tex("sentry_glow" + old), false);
			}
			case VOID_SUIT -> {
				if (exists(tex("sentry_void" + w))) {
					return pair(tex("sentry_void" + w), tex("sentry_void_glow" + w), false);
				}
				return pair(tex("void_form" + old), tex("void_glow" + old), true);
			}
			case VOID_BODY -> {
				return pair(tex("void_form" + old), tex("void_glow" + old), false);
			}
			default -> {
				return pair(tex("void_body"), tex("void_body_glow"), false);
			}
		}
	}

	private static @Nullable Pair pair(ResourceLocation baseId, ResourceLocation glowId, boolean headless) {
		NativeImage base = read(baseId);
		if (base == null) {
			return null;
		}
		NativeImage glow = read(glowId);
		if (!headless) {
			return new Pair(base, glow, baseId, glow == null ? null : glowId);
		}
		clearHead(base);
		if (glow != null) {
			clearHead(glow);
		}
		String n = baseId.getPath().replace('/', '_').replace(".png", "");
		return new Pair(base, glow, register(n + "_headless", base), glow == null ? null : register(n + "_glow_headless", glow));
	}

	/** Clears the head and hat regions (the top 16 rows): the wearer's own face and hair show. */
	private static void clearHead(NativeImage img) {
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 64; x++) {
				img.setPixelRGBA(x, y, 0);
			}
		}
	}

	/** The base texture of a look (null: missing). */
	public static @Nullable ResourceLocation base(Look look, boolean slim) {
		Pair p = look(look, slim);
		return p == null ? null : p.baseId;
	}

	public static @Nullable ResourceLocation glow(Look look, boolean slim) {
		Pair p = look(look, slim);
		return p == null ? null : p.glowId;
	}

	/**
	 * Step {@code k} (0..{@link #STEPS}) of the dissolve from {@code from} (null: nothing, the bare wearer) to {@code to}: {base, glow}
	 * (either may be null). Step 0 is {@code from}, the last step is {@code to}.
	 */
	public static ResourceLocation[] step(@Nullable Look from, Look to, boolean slim, int k) {
		k = Math.max(0, Math.min(STEPS, k));
		if (k == STEPS) {
			return new ResourceLocation[] { base(to, slim), glow(to, slim) };
		}
		if (k == 0) {
			return from == null ? new ResourceLocation[2] : new ResourceLocation[] { base(from, slim), glow(from, slim) };
		}
		String key = from + ">" + to + (slim ? "_slim_" : "_wide_") + k;
		ResourceLocation[] cached = STEP_CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		Pair a = from == null ? null : look(from, slim);
		Pair b = look(to, slim);
		ResourceLocation[] out = new ResourceLocation[2];
		if (b != null && b.base != null) {
			float t = k / (float) STEPS;
			float edge = 1f / STEPS;
			float[] ord = order();
			NativeImage base = new NativeImage(64, 64, true);
			NativeImage glow = new NativeImage(64, 64, true);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					float o = ord[y * 64 + x];
					boolean flipped = o < t;
					NativeImage src = flipped ? b.base : (a == null ? null : a.base);
					NativeImage gsrc = flipped ? b.glow : (a == null ? null : a.glow);
					int px = src == null ? 0 : src.getPixelRGBA(x, y);
					base.setPixelRGBA(x, y, px);
					int g = gsrc == null ? 0 : gsrc.getPixelRGBA(x, y);
					if (flipped && o >= t - edge && ((px >>> 24) & 0xFF) != 0) {
						g = 0xFFFF9AC8; // the burning edge: a violet-white fringe (ABGR)
					}
					glow.setPixelRGBA(x, y, g);
				}
			}
			String n = (from == null ? "none" : from.name().toLowerCase()) + "_" + to.name().toLowerCase() + (slim ? "_slim_" : "_wide_") + k;
			out[0] = register("fade_" + n, base);
			out[1] = register("fade_glow_" + n, glow);
			base.close();
			glow.close();
		}
		STEP_CACHE.put(key, out);
		return out;
	}

	/**
	 * The order texels flip in (0..1): mostly how far the texel is from the chest on the model, a little noise. Computed once from the
	 * player-skin unwrap (base layer boxes; each overlay box copies its base box).
	 */
	private static float[] order() {
		if (order != null) {
			return order;
		}
		float[] o = new float[64 * 64];
		java.util.Arrays.fill(o, 0.999f);
		// {u, v, w, h, d, x0, y0, z0} in model pixels (y down, z toward the back), the wide layout (slim arms are inside these)
		int[][] boxes = { { 0, 0, 8, 8, 8, -4, -8, -4 }, { 16, 16, 8, 12, 4, -4, 0, -2 }, { 40, 16, 4, 12, 4, -8, 0, -2 },
				{ 32, 48, 4, 12, 4, 4, 0, -2 }, { 0, 16, 4, 12, 4, -4, 12, -2 }, { 16, 48, 4, 12, 4, 0, 12, -2 } };
		int[][] overlays = { { 32, 0 }, { 16, 32 }, { 40, 32 }, { 48, 48 }, { 0, 32 }, { 0, 48 } };
		float max = 0f;
		float[] dist = new float[64 * 64];
		java.util.Arrays.fill(dist, -1f);
		for (int bi = 0; bi < boxes.length; bi++) {
			int[] b = boxes[bi];
			int u = b[0], v = b[1], w = b[2], h = b[3], d = b[4];
			float x0 = b[5], y0 = b[6], z0 = b[7];
			for (int f = 0; f < 6; f++) {
				int fu, fv, fw, fh;
				switch (f) {
					case 0 -> { fu = u + d; fv = v; fw = w; fh = d; } // top
					case 1 -> { fu = u + d + w; fv = v; fw = w; fh = d; } // bottom
					case 2 -> { fu = u; fv = v + d; fw = d; fh = h; } // right side
					case 3 -> { fu = u + d; fv = v + d; fw = w; fh = h; } // front
					case 4 -> { fu = u + d + w; fv = v + d; fw = d; fh = h; } // left side
					default -> { fu = u + d + w + d; fv = v + d; fw = w; fh = h; } // back
				}
				for (int j = 0; j < fh; j++) {
					for (int i = 0; i < fw; i++) {
						float px, py, pz;
						switch (f) {
							case 0 -> { px = x0 + i + 0.5f; py = y0; pz = z0 + j + 0.5f; }
							case 1 -> { px = x0 + i + 0.5f; py = y0 + h; pz = z0 + j + 0.5f; }
							case 2 -> { px = x0; py = y0 + j + 0.5f; pz = z0 + d - i - 0.5f; }
							case 3 -> { px = x0 + i + 0.5f; py = y0 + j + 0.5f; pz = z0; }
							case 4 -> { px = x0 + w; py = y0 + j + 0.5f; pz = z0 + i + 0.5f; }
							default -> { px = x0 + w - i - 0.5f; py = y0 + j + 0.5f; pz = z0 + d; }
						}
						// the heart: the front of the chest, a little left of centre
						float dx = px - 0.5f, dy = py - 4f, dz = pz + 2f;
						float dd = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
						int tx = fu + i, ty = fv + j;
						dist[ty * 64 + tx] = dd;
						int ox = overlays[bi][0] + (tx - u), oy = overlays[bi][1] + (ty - v);
						if (ox >= 0 && ox < 64 && oy >= 0 && oy < 64) {
							dist[oy * 64 + ox] = dd + 0.6f; // the outer layer a breath after the base under it
						}
						max = Math.max(max, dd + 0.6f);
					}
				}
			}
		}
		for (int k = 0; k < o.length; k++) {
			if (dist[k] >= 0f) {
				int x = k % 64, y = k / 64;
				int hsh = (x * 374761393 + y * 668265263);
				hsh = (hsh ^ (hsh >>> 13)) * 1274126177;
				float noise = ((hsh ^ (hsh >>> 16)) & 0xFFFF) / 65535f;
				o[k] = Math.min(0.999f, 0.75f * dist[k] / max + 0.25f * noise);
			}
		}
		order = o;
		return o;
	}

	/**
	 * The cape's two colours and their blend ({@code t} 0 = blue, 1 = black; {@code black}: the takeover's flat black). Built from the
	 * blue and black cape textures, 9 steps.
	 */
	public static @Nullable ResourceLocation cape(float t, boolean takeover) {
		if (takeover) {
			return capeStep(-1);
		}
		int k = Math.round(Math.max(0f, Math.min(1f, t)) * 8f);
		return capeStep(k);
	}

	private static @Nullable ResourceLocation capeStep(int k) {
		ResourceLocation blue = tex("sentry_cape_blue");
		ResourceLocation black = tex("sentry_cape_black");
		if (k == 0) {
			return exists(blue) ? blue : null;
		}
		if (k == 8) {
			return exists(black) ? black : null;
		}
		String key = "cape_" + k;
		ResourceLocation[] cached = STEP_CACHE.get(key);
		if (cached != null) {
			return cached[0];
		}
		NativeImage a = readCape(blue);
		NativeImage b = readCape(black);
		ResourceLocation id = null;
		if (a != null && b != null && a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight()) {
			NativeImage out = new NativeImage(a.getWidth(), a.getHeight(), true);
			float t = k < 0 ? 1f : k / 8f;
			for (int y = 0; y < a.getHeight(); y++) {
				for (int x = 0; x < a.getWidth(); x++) {
					int pa = a.getPixelRGBA(x, y), pb = b.getPixelRGBA(x, y);
					int c;
					if (k < 0) {
						c = (pa & 0xFF000000) | 0x00030203; // the takeover: flat black, the cloth's shape only
					} else {
						c = 0;
						for (int sh = 0; sh < 32; sh += 8) {
							int ca = (pa >>> sh) & 0xFF, cb = (pb >>> sh) & 0xFF;
							c |= (Math.round(ca + (cb - ca) * t) & 0xFF) << sh;
						}
					}
					out.setPixelRGBA(x, y, c);
				}
			}
			id = Minecraft.getInstance().getTextureManager().register("projecthero_sentry_" + key.replace('-', 'm'), new DynamicTexture(out));
			DYNAMIC.add(id);
		}
		if (a != null) {
			a.close();
		}
		if (b != null) {
			b.close();
		}
		STEP_CACHE.put(key, new ResourceLocation[] { id });
		return id;
	}

	private static @Nullable NativeImage readCape(ResourceLocation id) {
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream in = res.get().open()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			return null;
		}
	}
}
