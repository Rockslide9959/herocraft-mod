package com.projecthero.mod.client.frostgiant;

import java.util.WeakHashMap;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/**
 * v0.16.1: where the Frost Giant Assault's GeckoLib assets live, and how big to draw them.
 *
 * <p>The models are authored in plain model pixels (the placeholder box figures today, the generator's real models
 * later), so instead of a hard-coded {@code MODEL_SCALE} per model (the Titan's way) each giant's rest pose is measured
 * once from the baked geometry -- the lowest and highest vertex of every cube -- and scaled to fill its hit-box height.
 * Swapping in the real models needs no code change. Render thread only.
 */
public final class FrostModelFit {
	private static final Map<BakedGeoModel, Float> HEIGHTS = new WeakHashMap<>();

	private FrostModelFit() {
	}

	public static ResourceLocation geo(String model) {
		return ProjectHeroMod.id("geo/frost_giant/" + model + ".geo.json");
	}

	public static ResourceLocation texture(String model) {
		return ProjectHeroMod.id("textures/entity/frost_giant/" + model + ".png");
	}

	public static ResourceLocation animation(String model) {
		return ProjectHeroMod.id("animations/frost_giant/" + model + ".animation.json");
	}

	/** The rest pose's height in blocks (the vertical extent of every cube), measured once per baked model. */
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

	/** The scale that makes {@code model}'s rest pose {@code targetHeight} blocks tall. */
	public static float fit(BakedGeoModel model, float targetHeight) {
		return targetHeight / restHeight(model);
	}

	public static void clear() {
		HEIGHTS.clear();
	}
}
