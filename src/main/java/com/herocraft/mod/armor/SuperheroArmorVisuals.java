package com.herocraft.mod.armor;

import java.util.LinkedHashMap;
import java.util.Map;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.resources.ResourceLocation;

/**
 * The registry of {@link ArmorVisualDefinition}s, keyed by armour-set id ({@code thor},
 * {@code mark_1}, {@code mark_2}, {@code mark_iii}, {@code mark_4}, {@code mark_v}, {@code mark_6},
 * {@code mark_vii}). This is the one place that maps "which suit" to "which model / texture /
 * animation"; the shared {@link com.herocraft.mod.client.render.SuperheroArmorModel} just asks this
 * class.
 *
 * <p>{@code thor} uses the shared {@code crimson_vanguard} geometry + animation with its own texture.
 * Every Iron Man mark (1-7) ships its own {@code geo/<set>.geo.json} built from a bespoke plated model
 * around a real player-skin texture, but all still point at {@link #SHARED_ANIMATION} -- the geo files
 * reuse crimson_vanguard's bone names so the shared idle / assemble / helmet / flight clips drive them
 * too. See docs/ARMOR_MODELS.md.
 *
 * <p>Adding a brand-new armour set: register its item as a
 * {@link com.herocraft.mod.armor.SuperheroArmorItem} whose {@code armorSetId()} returns a new id, and
 * add a {@link #register} line for that id. If you forget, it falls back to {@link #DEFAULT}
 * (crimson_vanguard) rather than crashing.
 */
public final class SuperheroArmorVisuals {
	/** Shared geometry file (GeckoLib default armour bone names). */
	public static final ResourceLocation SHARED_GEO = HeroCraftMod.id("geo/crimson_vanguard.geo.json");
	/** Shared animation file (idle / assemble / disassemble / helmet / repulsor / flight / landing). */
	public static final ResourceLocation SHARED_ANIMATION = HeroCraftMod.id("animations/crimson_vanguard.animation.json");

	/** Used for any set that has not been registered. */
	public static final ArmorVisualDefinition DEFAULT = new ArmorVisualDefinition(
			SHARED_GEO, HeroCraftMod.id("textures/armor/crimson_vanguard.png"), SHARED_ANIMATION);

	private static final Map<String, ArmorVisualDefinition> BY_SET = new LinkedHashMap<>();

	static {
		// (The Asgardian armour set was removed in v0.6.22, so its "thor" visual definition is gone too.)

		// Every Iron Man mark now ships its OWN geometry: a bespoke plated model built around a 64x64
		// player-skin UV layout (<set>.png is the authoritative art -- do not repaint it). They all keep
		// the SHARED_ANIMATION file because SuperheroArmorItem's idle animation is looked up by the
		// literal name "animation.crimson_vanguard.idle"; the geos deliberately reuse crimson_vanguard's
		// bone names (helmet / faceplate / chest_armor / arc_reactor / <side>_shoulder / _gauntlet /
		// _thigh / _knee / _boot) so idle -- and the triggerable assemble / helmet / flight clips --
		// drive them too. See docs/ARMOR_MODELS.md.
		for (String setId : new String[] {
				"mark_1", "mark_2", "mark_iii", "mark_4", "mark_v", "mark_6", "mark_vii" }) {
			register(setId, new ArmorVisualDefinition(HeroCraftMod.id("geo/" + setId + ".geo.json"),
					HeroCraftMod.id("textures/armor/" + setId + ".png"), SHARED_ANIMATION));
		}
	}

	private SuperheroArmorVisuals() {
	}

	public static void register(String setId, ArmorVisualDefinition definition) {
		BY_SET.put(setId, definition);
	}

	public static ArmorVisualDefinition get(String setId) {
		return BY_SET.getOrDefault(setId, DEFAULT);
	}
}
