package com.projecthero.mod.client.symbiote;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.symbiote.SymbiotePet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.14.4 (pet hosts): a Symbiote Pet's skin spreads over it pixel by pixel when it transforms for combat, and
 * recedes the same way when it calms down -- the same technique as the player's suit-up ({@link SymbioteDissolve}):
 * for each pet texture we pre-build {@link #STEPS}+1 copies with more and more of its pixels turned to glossy
 * Symbiote black, and the body is drawn with the copy matching {@link SymbiotePet#formProgress} (the synced
 * transform tick, so every viewer sees the same spread). The swap happens in {@code LivingEntitySymbioteSkinMixin}
 * on the body's own texture, so the collar, wolf armour and the white eyes all still draw on top.
 *
 * <p>The order the pixels turn in: black blotches seeded at a handful of points grow outward (distance to the nearest
 * seed plus jitter), with a band of wet violet at the leading edge. The seeds depend only on the texture size, so a
 * wolf switching between its tame and angry textures mid-transform keeps the same pattern. Out of combat a pet keeps
 * {@link #DORMANT} of the skin -- a few black blotches -- as the hint that something lives in it.
 *
 * <p>Built lazily on the render thread, once per texture, and kept for the session (a few dozen 64x32 images).
 */
public final class SymbiotePetSkin {
	public static final int STEPS = 32;
	/** How much of the skin shows on a pet at rest. */
	public static final float DORMANT = 0.05f;
	/** Past this much skin the white eyes open. */
	public static final float EYES_AT = 0.7f;
	private static final int SEEDS = 7;
	private static final Map<ResourceLocation, ResourceLocation[]> CACHE = new HashMap<>();

	private SymbiotePetSkin() {
	}

	/** How much of the skin to show on this pet now: its form progress, never below the dormant hint. */
	public static float coverage(LivingEntity pet) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		return Math.max(DORMANT, SymbiotePet.formProgress(pet, pet.level().getGameTime(), partial));
	}

	/** The texture to draw a Symbiote Pet's body with, given its normal {@code base} texture. */
	public static ResourceLocation texture(LivingEntity pet, ResourceLocation base) {
		ResourceLocation[] frames = CACHE.computeIfAbsent(base, SymbiotePetSkin::build);
		if (frames == null || frames.length == 0) {
			return base;
		}
		int step = Math.max(0, Math.min(STEPS, Math.round(coverage(pet) * STEPS)));
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
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not build the Symbiote pet skin for {}: {}", base, e.toString());
			return new ResourceLocation[0];
		}
		int w = src.getWidth();
		int h = src.getHeight();
		List<int[]> opaque = new ArrayList<>();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (((src.getPixelRGBA(x, y) >>> 24) & 0xFF) != 0) {
					opaque.add(new int[]{x, y});
				}
			}
		}
		if (opaque.isEmpty()) {
			src.close();
			return new ResourceLocation[0];
		}
		java.util.Random rng = new java.util.Random(w * 31L + h * 7L + 1444L);
		int[][] seeds = new int[SEEDS][];
		for (int i = 0; i < SEEDS; i++) {
			seeds[i] = opaque.get(rng.nextInt(opaque.size()));
		}
		double span = Math.max(w, h) * 0.5;
		List<long[]> ranked = new ArrayList<>(opaque.size());
		for (int[] px : opaque) {
			double best = Double.MAX_VALUE;
			for (int[] s : seeds) {
				double dx = px[0] - s[0];
				double dy = px[1] - s[1];
				best = Math.min(best, Math.sqrt(dx * dx + dy * dy));
			}
			double key = Math.min(1.0, best / span) * 0.7 + rng.nextDouble() * 0.3;
			ranked.add(new long[]{(long) (key * 1_000_000L), px[0], px[1], rng.nextInt(10)});
		}
		ranked.sort((a, b) -> Long.compare(a[0], b[0]));
		int total = ranked.size();
		int band = Math.max(1, (int) Math.round(total * 1.6 / STEPS));
		ResourceLocation[] frames = new ResourceLocation[STEPS + 1];
		String tag = base.getNamespace() + "_" + base.getPath().replaceAll("[^a-z0-9_]", "_");
		for (int k = 0; k <= STEPS; k++) {
			int covered = (int) Math.round(total * (k / (double) STEPS));
			NativeImage img = new NativeImage(w, h, true);
			img.copyFrom(src);
			for (int i = 0; i < total; i++) {
				long[] px = ranked.get(i);
				int x = (int) px[1];
				int y = (int) px[2];
				int abgr = src.getPixelRGBA(x, y);
				if (i < covered) {
					img.setPixelRGBA(x, y, symbiote(abgr, (int) px[3]));
				} else if (k > 0 && k < STEPS && i < covered + band) {
					img.setPixelRGBA(x, y, edge(abgr));
				} else {
					break; // ranked order: nothing past the front changes
				}
			}
			ResourceLocation id = ProjectHeroMod.id("dynamic/symbiote_pet_skin/" + tag + "_" + k);
			mc.getTextureManager().register(id, new DynamicTexture(img));
			frames[k] = id;
		}
		src.close();
		return frames;
	}

	private static double luma(int abgr) {
		int r = abgr & 0xFF;
		int g = (abgr >> 8) & 0xFF;
		int b = (abgr >> 16) & 0xFF;
		return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
	}

	/** Glossy purple-black that keeps the fur's shading; one pixel in ten catches a wet highlight. NativeImage is ABGR. */
	private static int symbiote(int abgr, int sheen) {
		double l = luma(abgr);
		int boost = sheen == 0 ? 22 : 0;
		int r = (int) (12 + l * 30) + boost;
		int g = (int) (9 + l * 22) + boost;
		int b = (int) (18 + l * 40) + boost + (sheen == 0 ? 8 : 0);
		return (abgr & 0xFF000000) | (Math.min(255, b) << 16) | (Math.min(255, g) << 8) | Math.min(255, r);
	}

	/** The spreading front: the fur darkened and stained violet, where the goo is just reaching. */
	private static int edge(int abgr) {
		int r = abgr & 0xFF;
		int g = (abgr >> 8) & 0xFF;
		int b = (abgr >> 16) & 0xFF;
		int nr = (int) (r * 0.35 + 40);
		int ng = (int) (g * 0.25 + 14);
		int nb = (int) (b * 0.35 + 62);
		return (abgr & 0xFF000000) | (Math.min(255, nb) << 16) | (Math.min(255, ng) << 8) | Math.min(255, nr);
	}
}
