package com.projecthero.mod.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.projecthero.mod.behemoth.BehemothConfig;
import com.projecthero.mod.config.VersionedConfig;
import com.projecthero.mod.darkseid.DarkseidConfig;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.horde.HordeConfig;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.titan.TitanConfig;
import com.projecthero.mod.titanshifter.TitanShifterConfig;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * v0.14.21 config migration audit: every {@code config/projecthero*.json} goes through {@link VersionedConfig}. Each
 * test writes an old (or broken) file into the real config directory under its own {@code projecthero_gametest_*} name
 * -- deleting whatever an earlier run left there first, so a stale file in {@code build/run/gameTest/config} can never
 * decide the result -- loads it through the config's own spec, and checks both the values and the rewritten file. The
 * live configs the mod is running on are never touched.
 */
public class ConfigMigrationGameTests implements FabricGameTest {
	private static Path fresh(String name) {
		Path dir = FabricLoader.getInstance().getConfigDir();
		Path p = dir.resolve("projecthero_gametest_" + name + ".json");
		try {
			Files.createDirectories(dir);
			Files.deleteIfExists(p);
			for (Path backup : backups(p)) {
				Files.deleteIfExists(backup);
			}
		} catch (IOException e) {
			throw new GameTestAssertException("config io: " + e);
		}
		return p;
	}

	private static Path write(String name, String json) {
		Path p = fresh(name);
		try {
			Files.writeString(p, json);
		} catch (IOException e) {
			throw new GameTestAssertException("config io: " + e);
		}
		return p;
	}

	private static List<Path> backups(Path p) {
		String prefix = p.getFileName() + ".corrupt-";
		try (Stream<Path> s = Files.list(p.getParent())) {
			return s.filter(f -> f.getFileName().toString().startsWith(prefix)).toList();
		} catch (IOException e) {
			throw new GameTestAssertException("config io: " + e);
		}
	}

	private static JsonObject reread(Path p) {
		try {
			return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
		} catch (IOException e) {
			throw new GameTestAssertException("config io: " + e);
		}
	}

	private static double num(JsonObject o, String key) {
		return VersionedConfig.at(o, key).getAsDouble();
	}

	private static void cleanUp(Path p) {
		fresh(p.getFileName().toString().replace("projecthero_gametest_", "").replace(".json", ""));
	}

	/** The Titan file never had a version, so the v0.10.11 damage cut never reached a v0.9.22-v0.10.10 file. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void aStaleTitanConfigGetsTheV01011DamageCut(GameTestHelper helper) {
		Path p = write("titan", "{\"stats\":{\"health\":2500.0},"
				+ "\"attacks\":{\"punchDamage\":26.0,\"meleeDamage\":22.0,\"stompDamage\":40.0,\"chargeDamage\":44.0,\"throwDamage\":14.0},"
				+ "\"cooldowns\":{\"stomp\":100},"
				+ "\"world\":{\"spawnChance\":0.05,\"blockBlacklist\":[\"minecraft:bedrock\"]}}");
		TitanConfig c = TitanConfig.SPEC.load(p);
		helper.assertTrue(c.attacks.punchDamage == 20.0 && c.attacks.meleeDamage == 16.0 && c.attacks.stompDamage == 30.0
				&& c.attacks.chargeDamage == 34.0 && c.attacks.throwDamage == 10.0,
				"the old damage moved to today's values, punch " + c.attacks.punchDamage);
		helper.assertTrue(c.stats.health == 2500.0 && c.cooldowns.stomp == 100, "values that pass did not touch keep the file's");
		helper.assertTrue(c.world.spawnChance == 0.05 && c.world.blockBlacklist.equals(List.of("minecraft:bedrock")),
				"the server's world rules are kept");
		helper.assertTrue(c.aggro != null && c.aggro.switchMargin == 1.25 && c.attacks.roarRadius == 24.0,
				"keys newer than the file arrive at their defaults");
		helper.assertTrue(c.configVersion == TitanConfig.SPEC.currentVersion() && c.configVersion >= 1, "stamped");

		JsonObject f = reread(p);
		helper.assertTrue(f.get("configVersion").getAsInt() == TitanConfig.SPEC.currentVersion(), "the file is stamped");
		helper.assertTrue(num(f, "attacks.punchDamage") == 20.0 && num(f, "stats.health") == 2500.0
				&& num(f, "aggro.switchMargin") == 1.25, "the file was rewritten with the migrated values");

		// a current file keeps a value the server chose, even a "stale-looking" one
		write("titan", "{\"configVersion\":" + TitanConfig.SPEC.currentVersion() + ",\"attacks\":{\"punchDamage\":26.0}}");
		helper.assertTrue(TitanConfig.SPEC.load(p).attacks.punchDamage == 26.0, "a current-version file is left as the server set it");
		cleanUp(p);
		helper.succeed();
	}

	/** Behemoth: no version -> everything reset (the v0.13.7 rule); v1 -> only the health moves (v0.13.10). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void staleBehemothConfigsMigrateAsTheyAlwaysHave(GameTestHelper helper) {
		Path p = write("behemoth", "{\"stats\":{\"health\":30000.0,\"meleeDamage\":120.0},\"world\":{\"spawnChance\":0.5}}");
		BehemothConfig c = BehemothConfig.SPEC.load(p);
		helper.assertTrue(c.stats.health == 3000.0 && c.stats.meleeDamage == 80.0 && c.world.spawnChance == 0.0009,
				"an unversioned file is reset to today's defaults, health " + c.stats.health);

		write("behemoth", "{\"configVersion\":1,\"stats\":{\"health\":1000.0,\"meleeDamage\":120.0},\"world\":{\"spawnChance\":0.5}}");
		c = BehemothConfig.SPEC.load(p);
		helper.assertTrue(c.stats.health == 3000.0, "v1: health 1000 -> 3000, got " + c.stats.health);
		helper.assertTrue(c.stats.meleeDamage == 120.0 && c.world.spawnChance == 0.5, "v1: the rest of the file is kept");
		helper.assertTrue(reread(p).get("configVersion").getAsInt() == BehemothConfig.SPEC.currentVersion()
				&& num(reread(p), "stats.health") == 3000.0, "the file was rewritten");
		cleanUp(p);
		helper.succeed();
	}

	/** Hulk: v1 -> the v0.13.14 kit; v2 -> Thunderclap 12 moves to 25, a range the server chose stays. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void staleHulkConfigsMigrate(GameTestHelper helper) {
		Path p = write("hulk", "{\"configVersion\":1,\"abilities\":{\"groundSmashDamage\":16.0,\"leapMaxBlocks\":45.0},"
				+ "\"world\":{\"sprintSmashEnabled\":false}}");
		HulkConfig c = HulkConfig.SPEC.load(p);
		helper.assertTrue(c.abilities.groundSmashDamage == 30.0f && c.abilities.leapMaxBlocks == 70.0 && c.abilities.thunderclapRange == 25.0,
				"v1: the v0.13.14 kit numbers");
		helper.assertFalse(c.world.sprintSmashEnabled, "v1: the server's world switch is kept");

		write("hulk", "{\"configVersion\":2,\"abilities\":{\"thunderclapRange\":12.0,\"powerPunchDamage\":40.0}}");
		c = HulkConfig.SPEC.load(p);
		helper.assertTrue(c.abilities.thunderclapRange == 25.0 && c.abilities.powerPunchDamage == 40.0f,
				"v2: the old 12-block default moves up, other tuning stays");
		write("hulk", "{\"configVersion\":2,\"abilities\":{\"thunderclapRange\":18.0}}");
		helper.assertTrue(HulkConfig.SPEC.load(p).abilities.thunderclapRange == 18.0, "v2: a range the server chose stays");
		helper.assertTrue(reread(p).get("configVersion").getAsInt() == HulkConfig.SPEC.currentVersion(), "the file is stamped");
		cleanUp(p);
		helper.succeed();
	}

	/** Titan Shifter: an unversioned (v0.12.31) file runs every step. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void aStaleTitanShifterConfigRunsEveryStep(GameTestHelper helper) {
		Path p = write("titan_shifter", "{\"stats\":{\"widthBlocks\":4.0,\"maxHealth\":650.0},\"transformation\":{\"cooldownTicks\":1200},"
				+ "\"energy\":{\"transformMinFraction\":0.9,\"baseFormRegenAmplifier\":2},\"abilities\":{\"roarRadius\":12.0},"
				+ "\"effects\":{\"footsteps\":false},\"controls\":{\"transformKey\":\"J\"}}");
		TitanShifterConfig c = TitanShifterConfig.SPEC.load(p);
		helper.assertTrue(Math.abs(c.stats.widthBlocks - 3.67) < 1e-9 && c.transformation.cooldownTicks == 0, "v2 values");
		helper.assertTrue(c.energy.transformMinFraction == 1.0, "v3 value");
		helper.assertTrue(c.energy.baseFormRegenAmplifier == 1 && c.abilities.roarRadius == 32.0, "v4 values");
		helper.assertTrue(c.stats.maxHealth == 650.0 && !c.effects.footsteps, "everything else is kept");
		JsonObject f = reread(p);
		helper.assertTrue(f.get("controls") == null, "a section the mod no longer has is dropped from the file");
		helper.assertTrue(f.get("configVersion").getAsInt() == 4 && num(f, "abilities.roarRadius") == 32.0, "the file was rewritten");
		cleanUp(p);
		helper.succeed();
	}

	/** Darkseid through the real loader (the JSON-only tests live in DarkseidRaidGameTests). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void aVersionTwoDarkseidFileOnDiskMigrates(GameTestHelper helper) {
		Path p = write("darkseid", "{\"configVersion\":2,\"raid\":{\"maxParticipants\":6},"
				+ "\"parademons\":{\"standardHealth\":36.0,\"bruteDamage\":17.25,\"flightSpeed\":0.5}}");
		DarkseidConfig c = DarkseidConfig.SPEC.load(p);
		helper.assertTrue(DarkseidConfig.SPEC.currentVersion() == 3 && c.configVersion == 3, "stamped v3");
		helper.assertTrue(c.parademons.standardHealth == 30.0 && c.parademons.bruteDamage == 15.0, "Parademon stats restored");
		helper.assertTrue(c.parademons.flightSpeed == 0.5 && c.raid.maxParticipants == 6, "other values kept");
		helper.assertTrue(c.motherBoxes != null && c.rewards != null && c.boss.baseDarkseidHealth == 3000.0, "missing sections filled in");
		helper.assertTrue(num(reread(p), "parademons.standardHealth") == 30.0, "the file was rewritten");
		cleanUp(p);
		helper.succeed();
	}

	/** Events through the real loader: the v0.14.4 Grave Essence step. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void anUnversionedEventFileOnDiskMigrates(GameTestHelper helper) {
		Path p = write("events", "{\"zombieRaid\":{\"graveEssenceFromBasicChance\":0.5,\"graveEssenceFromFinalBossMax\":40,"
				+ "\"waveCount\":9},\"framework\":{\"eventRadius\":80.0}}");
		EventConfig c = EventConfig.SPEC.load(p);
		helper.assertTrue(c.zombieRaid.graveEssenceFromBasicChance == 0.06 && c.zombieRaid.graveEssenceFromFinalBossMax == 18,
				"drops reset");
		helper.assertTrue(c.zombieRaid.waveCount == 9 && c.framework.eventRadius == 80.0, "server tuning kept");
		helper.assertTrue(c.supervillainRaid != null && c.supervillainRaid.bossMeleeDamage == 16.0, "a missing section filled in");
		helper.assertTrue(reread(p).get("configVersion").getAsInt() == EventConfig.SPEC.currentVersion(), "the file is stamped");
		cleanUp(p);
		helper.succeed();
	}

	/** HeroConfig had no version and no default ever changed: stamping it must keep every value. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void anUnversionedHeroConfigKeepsEveryValue(GameTestHelper helper) {
		Path p = write("hero", "{\"mutationCapacity\":5,\"cooldownMultiplier\":2.0,\"abilityPvpDamage\":false,\"oldRemovedKey\":7}");
		HeroConfig c = HeroConfig.SPEC.load(p);
		helper.assertTrue(c.mutationCapacity == 5 && c.cooldownMultiplier == 2.0 && !c.abilityPvpDamage, "values kept");
		helper.assertTrue(c.symbioteHostChance == 0.0015, "a missing key gets its default");
		JsonObject f = reread(p);
		helper.assertTrue(f.get("oldRemovedKey") == null && f.get("configVersion").getAsInt() == 1, "unknown key dropped, file stamped");
		cleanUp(p);
		helper.succeed();
	}

	/** A file that is not JSON is backed up and regenerated, never a crash. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void aCorruptConfigIsBackedUpAndRegenerated(GameTestHelper helper) {
		String broken = "{ \"stats\": { \"health\": 2500.0, ";
		Path p = write("corrupt", broken);
		TitanConfig c = TitanConfig.SPEC.load(p);
		helper.assertTrue(c.stats.health == 1800.0 && c.attacks.punchDamage == 20.0, "today's defaults");
		List<Path> backups = backups(p);
		helper.assertTrue(backups.size() == 1, "one backup, got " + backups.size());
		try {
			helper.assertTrue(Files.readString(backups.get(0)).equals(broken), "the backup holds the broken file as it was");
		} catch (IOException e) {
			throw new GameTestAssertException("config io: " + e);
		}
		helper.assertTrue(reread(p).get("configVersion").getAsInt() == TitanConfig.SPEC.currentVersion(), "a valid file was written");

		write("corrupt", "[1, 2, 3]");
		helper.assertTrue(TitanConfig.SPEC.load(p).stats.health == 1800.0 && backups(p).size() == 1, "not an object: backed up too");
		cleanUp(p);
		helper.succeed();
	}

	/** A value of the wrong kind falls back to its default without throwing the rest of the file away. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void aWrongTypedValueGetsItsDefaultAndTheRestIsKept(GameTestHelper helper) {
		Path p = write("types", "{\"configVersion\":2,\"stats\":{\"health\":\"lots\",\"meleeDamage\":90.0,\"meleeRange\":\"8\"},"
				+ "\"abilities\":{\"fireballCooldownTicks\":12.5,\"hellfireCount\":{}},\"world\":{\"naturalSpawnEnabled\":\"yes\"},"
				+ "\"phases\":null}");
		BehemothConfig c = BehemothConfig.SPEC.load(p);
		helper.assertTrue(c.stats.health == 3000.0 && c.abilities.fireballCooldownTicks == 100 && c.abilities.hellfireCount == 6
				&& c.world.naturalSpawnEnabled, "bad values back to their defaults");
		helper.assertTrue(c.stats.meleeDamage == 90.0 && c.stats.meleeRange == 8.0, "good values (and a number written as text) kept");
		helper.assertTrue(c.phases != null && c.phases.phase2HealthFraction == 0.60, "a null section filled in");
		helper.assertTrue(backups(p).isEmpty(), "no backup: the file was repaired, not thrown away");
		cleanUp(p);
		helper.succeed();
	}

	/** A file from a newer release (a downgrade) runs no steps and keeps its version. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void aFileFromANewerReleaseIsLeftAlone(GameTestHelper helper) {
		Path p = write("newer", "{\"configVersion\":99,\"bosses\":{\"boneTyrantHealth\":5.0}}");
		HordeConfig c = HordeConfig.SPEC.load(p);
		helper.assertTrue(c.bosses.boneTyrantHealth == 5.0 && c.bosses.broodQueenHealth == 1400.0, "kept, gaps filled");
		helper.assertTrue(reread(p).get("configVersion").getAsInt() == 99, "its version is not lowered");
		cleanUp(p);
		helper.succeed();
	}

	/** Every config the mod reads is on the shared loader, and the live file on disk carries its current version. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyConfigIsVersionedAndStamped(GameTestHelper helper) {
		List<VersionedConfig<?>> all = List.of(HeroConfig.SPEC, EventConfig.SPEC, TitanConfig.SPEC, TitanShifterConfig.SPEC,
				HulkConfig.SPEC, BehemothConfig.SPEC, HordeConfig.SPEC, DarkseidConfig.SPEC);
		for (VersionedConfig<?> spec : all) {
			helper.assertTrue(spec.currentVersion() >= 1, spec.fileName() + " has a version");
			helper.assertFalse(spec.balanceKeys().isEmpty(), spec.fileName() + " declares its balance-owned keys");
			Path live = spec.path();
			helper.assertTrue(Files.exists(live), spec.fileName() + " was written at start-up");
			int onDisk = reread(live).get("configVersion").getAsInt();
			helper.assertTrue(onDisk >= spec.currentVersion(), spec.fileName() + " on disk is v" + onDisk + ", want v" + spec.currentVersion());
		}
		helper.succeed();
	}
}
