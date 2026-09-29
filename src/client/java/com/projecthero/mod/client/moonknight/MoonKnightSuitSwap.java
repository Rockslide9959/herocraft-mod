package com.projecthero.mod.client.moonknight;

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
 * v0.13.21: an alter change rematerialises the new alter's suit over the old one, pixel by pixel. The three alter
 * suits share one geometry and one UV layout (the user's three models are identical 64x64 player-skin rigs), so the
 * blend is done in the texture: for each (old, new) pair we build {@link #STEPS} composites, each taking a few more
 * of its pixels from the new suit and the rest from the old one, and the armour renderer draws the composite that
 * matches the swap clock. The order is random within three waves -- chest first, then the limbs, then the head --
 * the same sweep as the H materialise ({@code SymbioteDissolve}), so the change reads as the same magic.
 *
 * <p>Built lazily on the render thread, once per pair, and kept for the session (6 pairs at most).
 */
public final class MoonKnightSuitSwap {
	public static final int STEPS = 32;
	private static final Map<String, ResourceLocation[]> CACHE = new HashMap<>();

	private MoonKnightSuitSwap() {
	}

	/** The texture to draw for a suit {@code progress} (0..1) of the way from {@code from} to {@code to}. */
	public static ResourceLocation texture(ResourceLocation from, ResourceLocation to, float progress) {
		if (progress >= 0.999f || from.equals(to)) {
			return to;
		}
		ResourceLocation[] frames = CACHE.computeIfAbsent(from + ">" + to, k -> build(from, to));
		if (frames == null || frames.length == 0) {
			return to;
		}
		int step = Math.max(0, Math.min(STEPS - 1, (int) Math.floor(progress * STEPS)));
		return frames[step];
	}

	private static NativeImage read(ResourceLocation id) {
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream in = res.get().open()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not read {} for the Moon Knight suit swap: {}", id, e.toString());
			return null;
		}
	}

	private static ResourceLocation[] build(ResourceLocation from, ResourceLocation to) {
		NativeImage a = read(from);
		NativeImage b = read(to);
		if (a == null || b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
			if (a != null) {
				a.close();
			}
			if (b != null) {
				b.close();
			}
			return new ResourceLocation[0];
		}
		int w = a.getWidth();
		int h = a.getHeight();
		// every pixel either suit covers, ranked: wave (chest / limbs / head) plus a random jitter across the waves
		java.util.Random rng = new java.util.Random((from.hashCode() * 31L) ^ to.hashCode() ^ 2113L);
		List<int[]> ranked = new ArrayList<>();
		List<Double> keys = new ArrayList<>();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int pa = a.getPixelRGBA(x, y);
				int pb = b.getPixelRGBA(x, y);
				if (pa == pb) {
					continue; // nothing changes here
				}
				double u = x * 64.0 / w;
				double v = y * 64.0 / h;
				ranked.add(new int[]{x, y});
				keys.add(wave(u, v) * 0.34 + rng.nextDouble() * 0.5);
			}
		}
		Integer[] order = new Integer[ranked.size()];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, (i, j) -> Double.compare(keys.get(i), keys.get(j)));
		int total = order.length;
		ResourceLocation[] frames = new ResourceLocation[STEPS];
		String tag = (from.getPath() + "_to_" + to.getPath()).replaceAll("[^a-z0-9_]", "_");
		for (int k = 0; k < STEPS; k++) {
			int changed = (int) Math.round(total * (k / (double) STEPS));
			NativeImage img = new NativeImage(w, h, true);
			img.copyFrom(a);
			for (int i = 0; i < changed; i++) {
				int[] px = ranked.get(order[i]);
				img.setPixelRGBA(px[0], px[1], b.getPixelRGBA(px[0], px[1]));
			}
			ResourceLocation id = ProjectHeroMod.id("dynamic/moon_knight_swap/" + tag + "_" + k);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
			frames[k] = id;
		}
		a.close();
		b.close();
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
