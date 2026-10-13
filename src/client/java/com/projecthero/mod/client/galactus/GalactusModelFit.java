package com.projecthero.mod.client.galactus;

import java.util.Map;
import java.util.WeakHashMap;

import com.projecthero.mod.galactus.GalactusAnims;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/**
 * v0.16.1: how big to draw a Galactus GeckoLib model. Each model's rest pose is measured once from the baked geometry --
 * the lowest and highest vertex -- and scaled to its target height; bones named with a
 * {@link GalactusAnims#FIT_IGNORE_PREFIXES} prefix (the helmet's prongs / antennae / crest) are left out, so Galactus is
 * exactly {@code renderHeight} (60) blocks from the top of his head to his toes, as the user specified. Swapping in the
 * real models needs no code change. Render thread only.
 */
public final class GalactusModelFit {
	private static final Map<BakedGeoModel, Float> HEIGHTS = new WeakHashMap<>();

	private GalactusModelFit() {
	}

	public static float restHeight(BakedGeoModel model) {
		return HEIGHTS.computeIfAbsent(model, m -> {
			float[] mm = { Float.MAX_VALUE, -Float.MAX_VALUE };
			for (GeoBone b : m.topLevelBones()) {
				walk(b, mm);
			}
			float h = mm[1] - mm[0];
			return h > 0.01f && h < 4000f ? h : 1.0f;
		});
	}

	private static boolean ignored(String bone) {
		String n = bone.toLowerCase(java.util.Locale.ROOT);
		for (String p : GalactusAnims.FIT_IGNORE_PREFIXES) {
			if (n.startsWith(p)) {
				return true;
			}
		}
		return false;
	}

	private static void walk(GeoBone bone, float[] mm) {
		if (ignored(bone.getName())) {
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

	/** The scale that makes {@code model}'s rest pose (prongs excluded) {@code targetHeight} blocks tall. */
	public static float fit(BakedGeoModel model, float targetHeight) {
		return targetHeight / restHeight(model);
	}

	public static void clear() {
		HEIGHTS.clear();
	}
}
