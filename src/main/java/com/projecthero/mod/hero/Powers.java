package com.projecthero.mod.hero;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.resources.ResourceLocation;

/**
 * The registry of all 27 experimental powers. Definitions are built in {@link PowerCatalog}; ability
 * mechanics are attached separately in {@link AbilityHandlers}.
 */
public final class Powers {
	private static final Map<ResourceLocation, Power> BY_ID = new LinkedHashMap<>();
	private static final Map<String, Power> BY_KEY = new LinkedHashMap<>();

	/**
	 * v0.14.8: THE switch for which experimental powers are live. Only the powers that have been remade are enabled;
	 * every other power stays registered (its code, handlers, items, potions, recipe JSON and lang all remain) but
	 * cannot be obtained by any path -- serums / exposure triggers / lab devices, brewing, reagent crafting (the
	 * {@code projecthero:power_enabled} recipe condition), chest loot, random serums, research notes, the admin
	 * command -- is hidden from the creative tab, the guidebook and the power wheel, and is pruned from players who
	 * still hold it when they join ({@link ExperimentalPowers#pruneRemovedPowers}).
	 *
	 * <p>Re-enabling a power later = adding its key here. See docs/HEROPACK_CONTENT_REFERENCE.md.
	 */
	public static final Set<String> ENABLED = Set.of(
			"power_01_super_strength",
			"power_02_laser_vision",
			"power_04_super_speed",
			"power_12_super_regeneration");

	/**
	 * v0.14.13: enabled powers that are HERO-TIER, not mutations. Super Speed still runs on the mutation engine (its keys,
	 * HUD, cooldowns and passives live in {@link ExperimentalPowers}), but it is obtained like a hero
	 * ({@code com.projecthero.mod.flash.SpeedForce}) and held as a Primary power of its own ({@link HeroTiers#HERO_KEYS}
	 * "super_speed") -- so every MUTATION acquisition path (serums, exposure triggers, brewing, reagents, research notes,
	 * random serums, the power wheel) skips it: they all go through {@link #mutations()} / {@link #isMutation}.
	 */
	public static final Set<String> HERO_TIER = Set.of("power_04_super_speed");

	private Powers() {
	}

	public static Power register(Power power) {
		if (BY_ID.putIfAbsent(power.id(), power) != null) {
			throw new IllegalStateException("duplicate power id " + power.id());
		}
		BY_KEY.put(power.key(), power);
		return power;
	}

	public static Power get(ResourceLocation id) {
		return BY_ID.get(id);
	}

	public static Power byKey(String key) {
		return BY_KEY.get(key);
	}

	public static Collection<Power> all() {
		return BY_ID.values();
	}

	/** Whether the power with {@code key} is registered AND enabled ({@link #ENABLED}). */
	public static boolean isEnabled(String key) {
		return key != null && ENABLED.contains(key) && BY_KEY.containsKey(key);
	}

	public static boolean isEnabled(Power power) {
		return power != null && isEnabled(power.key());
	}

	/** v0.14.13: whether {@code key} is an enabled power run by the mutation engine but held as a Hero-Tier power. */
	public static boolean isHeroTier(String key) {
		return isEnabled(key) && HERO_TIER.contains(key);
	}

	public static boolean isHeroTier(Power power) {
		return power != null && isHeroTier(power.key());
	}

	/** v0.14.13: an enabled power that is a real mutation (obtainable through serums / exposures), not a Hero-Tier one. */
	public static boolean isMutation(Power power) {
		return isEnabled(power) && !HERO_TIER.contains(power.key());
	}

	/** v0.14.13: the enabled MUTATIONS (no Hero-Tier powers), in registration order -- what mutation acquisition offers. */
	public static List<Power> mutations() {
		return BY_ID.values().stream().filter(Powers::isMutation).toList();
	}

	/** The enabled powers, in registration order (Hero-Tier Super Speed included -- see {@link #mutations()}). */
	public static List<Power> enabled() {
		return BY_ID.values().stream().filter(Powers::isEnabled).toList();
	}

	public static int count() {
		return BY_ID.size();
	}

	public static ResourceLocation id(String path) {
		return ProjectHeroMod.id(path);
	}

	public static void initialize() {
		PowerCatalog.registerAll();
		ProjectHeroMod.LOGGER.info("[ProjectHero] registered {} experimental powers ({} ability handlers wired)",
				count(), AbilityHandlers.count());
	}
}
