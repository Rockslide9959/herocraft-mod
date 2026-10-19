package com.projecthero.mod.client.render.raid;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/**
 * v0.16.1: where the raid undead GeckoLib assets live ({@code geo|textures/entity|animations/raid/<model>}) and how big to
 * draw them: each model's rest pose is measured once from the baked geometry (leaving out crowns, weapons, hair, wisps and
 * the boss's accent sets, so the BODY fills the hit-box) and scaled to the mob's adult hit-box height. Babies are then
 * halved by GeckoLib's own native-scale step. Render thread only. Must match {@code FIT_IGNORE} in
 * {@code scratchpad/raid_undead_export.js}.
 */
public final class RaidModelFit {
	private static final Map<BakedGeoModel, Float> HEIGHTS = new WeakHashMap<>();
	private static final Set<String> FIT_IGNORE = Set.of("crown");

	private RaidModelFit() {
	}

	/** v0.16.7 (perf): asked for every frame by the renderers -- built once per name. */
	private static final java.util.Map<String, ResourceLocation> GEO_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	public static ResourceLocation geo(String model) {
		return GEO_CACHE.computeIfAbsent(model, k -> ProjectHeroMod.id("geo/raid/" + k + ".geo.json"));
	}

	/** v0.16.7 (perf): asked for every frame by the renderers -- built once per name. */
	private static final java.util.Map<String, ResourceLocation> TEXTURE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	public static ResourceLocation texture(String model) {
		return TEXTURE_CACHE.computeIfAbsent(model, k -> ProjectHeroMod.id("textures/entity/raid/" + k + ".png"));
	}

	/** v0.16.7 (perf): asked for every frame by the renderers -- built once per name. */
	private static final java.util.Map<String, ResourceLocation> ANIMATION_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	public static ResourceLocation animation(String model) {
		return ANIMATION_CACHE.computeIfAbsent(model, k -> ProjectHeroMod.id("animations/raid/" + k + ".animation.json"));
	}

	public static float restHeight(BakedGeoModel model) {
		return HEIGHTS.computeIfAbsent(model, m -> {
			float[] mm = { Float.MAX_VALUE, -Float.MAX_VALUE };
			for (GeoBone b : m.topLevelBones()) {
				walk(b, mm);
			}
			float h = mm[1] - mm[0];
			return h > 0.01f && h < 1000f ? h : 1.0f;
		});
	}

	private static void walk(GeoBone bone, float[] mm) {
		String n = bone.getName();
		if (FIT_IGNORE.contains(n) || n.startsWith("acc_") || n.startsWith("wing_")) {
			return;
		}
		for (GeoCube cube : bone.getCubes()) {
			for (GeoQuad quad : cube.quads()) {
				if (quad == null) {
					continue;
				}
				for (GeoVertex v : quad.vertices()) {
					mm[0] = Math.min(mm[0], v.position().y());
					mm[1] = Math.max(mm[1], v.position().y());
				}
			}
		}
		for (GeoBone child : bone.getChildBones()) {
			walk(child, mm);
		}
	}

	public static float fit(BakedGeoModel model, float targetHeight) {
		return targetHeight / restHeight(model);
	}

	public static void clear() {
		HEIGHTS.clear();
	}
}
