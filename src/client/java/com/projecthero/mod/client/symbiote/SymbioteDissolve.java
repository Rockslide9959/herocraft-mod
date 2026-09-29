package com.projecthero.mod.client.symbiote;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.projecthero.mod.ProjectHeroMod;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * v0.13.19: the Symbiote suit materialises one pixel at a time. Instead of hiding whole bones (and covering the
 * gaps with black particles), the suit's own texture is revealed pixel by pixel over the transform clock: for
 * each suit texture we build {@link #STEPS} copies, each showing a few more of its pixels, and the armour renderer
 * swaps to the copy matching the current progress. The order is random within three waves -- chest first, then
 * arms and legs, then the head -- so the living black crawls outward over the body, and the host's own skin shows
 * through wherever it has not reached yet.
 *
 * <p>Region lookup assumes the standard player-skin UV layout every suit here uses (scaled for HD textures).
 * Built lazily on the render thread, once per texture, and kept for the session.
 */
public final class SymbioteDissolve {
	public static final int STEPS = 32;
	private static final Map<ResourceLocation, ResourceLocation[]> CACHE = new HashMap<>();

	private SymbioteDissolve() {
	}

	/** The texture to draw for a suit that is {@code progress} (0..1) of the way on. */
	public static ResourceLocation texture(ResourceLocation base, float progress) {
		if (progress >= 0.999f) {
			return base;
		}
		ResourceLocation[] frames = CACHE.computeIfAbsent(base, SymbioteDissolve::build);
		if (frames == null || frames.length == 0) {
			return base;
		}
		int step = Math.max(0, Math.min(STEPS - 1, (int) Math.floor(progress * STEPS)));
		return frames[step];
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
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not build the Symbiote dissolve for {}: {}", base, e.toString());
			return new ResourceLocation[0];
		}
		int w = src.getWidth();
		int h = src.getHeight();
		// every opaque pixel, ranked: wave (chest / limbs / head) plus a random jitter that overlaps the waves
		java.util.Random rng = new java.util.Random(base.hashCode() * 31L + 1319L);
		List<long[]> ranked = new ArrayList<>();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int alpha = (src.getPixelRGBA(x, y) >>> 24) & 0xFF;
				if (alpha == 0) {
					continue;
				}
				double u = x * 64.0 / w;
				double v = y * 64.0 / h;
				double key = wave(u, v) * 0.34 + rng.nextDouble() * 0.5;
				ranked.add(new long[]{(long) (key * 1_000_000L), x, y});
			}
		}
		ranked.sort((a, b) -> Long.compare(a[0], b[0]));
		int total = ranked.size();
		ResourceLocation[] frames = new ResourceLocation[STEPS];
		String tag = base.getNamespace() + "_" + base.getPath().replaceAll("[^a-z0-9_]", "_");
		for (int k = 0; k < STEPS; k++) {
			int visible = (int) Math.round(total * (k / (double) STEPS));
			NativeImage img = new NativeImage(w, h, true);
			img.copyFrom(src);
			for (int i = visible; i < total; i++) {
				long[] px = ranked.get(i);
				img.setPixelRGBA((int) px[1], (int) px[2], 0);
			}
			ResourceLocation id = ProjectHeroMod.id("dynamic/symbiote_dissolve/" + tag + "_" + k);
			mc.getTextureManager().register(id, new DynamicTexture(img));
			frames[k] = id;
		}
		src.close();
		return frames;
	}

	/** 0 = torso, 1 = arms and legs, 2 = head -- in 64x64 player-skin UV space (both layers). */
	private static int wave(double u, double v) {
		if (v < 16) {
			return 2;
		}
		boolean torso = (u >= 16 && u < 40 && v >= 16 && v < 48);
		return torso ? 0 : 1;
	}
}
