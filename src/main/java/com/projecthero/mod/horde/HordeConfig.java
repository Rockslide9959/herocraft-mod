package com.projecthero.mod.horde;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * v0.14.20: horde boss health, at {@code config/projecthero_horde.json}. Before this the Bone Tyrant and Brood Queen
 * had their health hard-coded and the Zombie horde's Titan read the wilderness Titan's 1,800 from
 * {@code projecthero_titan.json} -- so lowering "the horde bosses' health" never had a file to change.
 *
 * <p>{@code configVersion} is a boxed, uninitialised {@code Integer} so Gson leaves it null for a file that predates it
 * (the same trick as {@code BehemothConfig}): a future balance pass bumps {@link #CONFIG_VERSION} and {@link #load}
 * resets the health numbers of an older file instead of keeping stale ones forever.
 */
public final class HordeConfig {
	private static final int CONFIG_VERSION = 1;

	private static HordeConfig instance = new HordeConfig();

	public Integer configVersion;
	public Bosses bosses = new Bosses();

	/** Health for one fighter, plus this much per extra fighter, never past {@link #maxHealth}. */
	public static final class Bosses {
		/** The Titan the Zombie horde ends with (the wilderness Titan keeps projecthero_titan.json's health). */
		public double zombieTitanHealth = 1000.0;
		public double boneTyrantHealth = 1200.0;
		public double broodQueenHealth = 1400.0;
		public double healthPerExtraFighter = 300.0;
		public double maxHealth = 4000.0;
	}

	public static Bosses bosses() {
		return instance.bosses;
	}

	/** {@code base} for one fighter, scaled up for the rest of the party, capped. */
	public static double healthFor(double base, int players) {
		Bosses b = instance.bosses;
		return Math.min(b.maxHealth, base + b.healthPerExtraFighter * Math.max(0, players - 1));
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("projecthero_horde.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				HordeConfig loaded = gson.fromJson(Files.readString(path), HordeConfig.class);
				if (loaded != null) {
					instance = loaded;
					if (instance.bosses == null || instance.configVersion == null || instance.configVersion < CONFIG_VERSION) {
						instance.bosses = new Bosses();
					}
				}
			}
			instance.configVersion = CONFIG_VERSION;
			Files.writeString(path, gson.toJson(instance));
		} catch (IOException | JsonSyntaxException e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not load horde config, using defaults", e);
			instance = new HordeConfig();
		}
	}
}
