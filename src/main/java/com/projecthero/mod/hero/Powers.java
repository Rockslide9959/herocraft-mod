package com.projecthero.mod.hero;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.resources.ResourceLocation;

/**
 * The registry of all 27 experimental powers. Definitions are built in {@link PowerCatalog}; ability
 * mechanics are attached separately in {@link AbilityHandlers}.
 */
public final class Powers {
	private static final Map<ResourceLocation, Power> BY_ID = new LinkedHashMap<>();
	private static final Map<String, Power> BY_KEY = new LinkedHashMap<>();

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
