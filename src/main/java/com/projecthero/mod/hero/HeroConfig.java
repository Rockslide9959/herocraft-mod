package com.projecthero.mod.hero;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-side, hand-rolled JSON config at {@code config/projecthero.json}. Deliberately dependency-free
 * (GSON is already on the classpath via Minecraft). Loaded once at mod init; missing file is written
 * with defaults. All experimental-power tuning that a server/modpack might want to change lives here.
 *
 * <p>Thor is intentionally NOT configured here -- its balance constants stay where they are, so
 * enabling/disabling experimental knobs can never change Thor behaviour.
 */
public final class HeroConfig {
	private static HeroConfig instance = new HeroConfig();

	// ---- mutation ----
	/** Maximum minor mutations a single player may permanently own. Spec default: 3. */
	public int mutationCapacity = 3;
	/** Ticks the "Unstable Mutation" serum effect lasts before the exposure window closes. */
	public int unstableMutationDurationTicks = 60 * 20;

	// ---- abilities ----
	/** Global multiplier applied to every experimental ability's base cooldown. */
	public double cooldownMultiplier = 1.0;
	/** If false, abilities that would break/place blocks only produce cosmetic/temporary effects. */
	public boolean abilityTerrainDamage = true;
	/** If false, experimental abilities deal no damage to other players. */
	public boolean abilityPvpDamage = true;
	/** If false, fire-creating abilities never spread fire to the world. */
	public boolean abilityFireSpread = false;
	/** If false, hard crowd control (prisons, grabs, stuns) cannot be applied to players at all. */
	public boolean abilityHardCrowdControlOnPlayers = true;
	/** Multiplier on how much world-changing a large ultimate is allowed to do. */
	public double largeAbilityDestruction = 1.0;

	// ---- worldgen ----
	/** Multiplier on structure spacing (>1 = rarer). Applied by the placement config where possible. */
	public double structureRarityMultiplier = 1.0;

	// ---- symbiote ----
	/**
	 * v0.9.10: chance a naturally spawning hostile mob is a rare <b>Symbiote Host</b> -- buffed, black
	 * particle aura, and it drops a free-floating Symbiote when killed. 0 disables the host spawn
	 * entirely (the meteor crater and the lab structure still generate). Default 0.0015 (~1 in 650).
	 */
	public double symbioteHostChance = 0.0015;

	private HeroConfig() {
	}

	public static HeroConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("projecthero.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				String json = Files.readString(path);
				HeroConfig loaded = gson.fromJson(json, HeroConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			}
			// Always (re)write so new keys appear for the user after a mod update.
			Files.createDirectories(path.getParent());
			Files.writeString(path, gson.toJson(instance));
		} catch (IOException | JsonSyntaxException e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not load config, using defaults", e);
			instance = new HeroConfig();
		}
	}

	public int scaledCooldown(int baseTicks) {
		if (baseTicks <= 0) {
			return 0;
		}
		return Math.max(1, (int) Math.round(baseTicks * cooldownMultiplier));
	}
}
