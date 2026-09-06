package com.projecthero.mod.event;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-side balance config for the world-event framework and the Zombie Raid, at
 * {@code config/projecthero_events.json}. Same hand-rolled GSON approach as {@link
 * com.projecthero.mod.hero.HeroConfig} -- deliberately a <em>separate</em> file so raid tuning can never
 * disturb power tuning (and so a modpack can ship one without the other).
 *
 * <p>Split into two sections on purpose (spec section 47: "do not put every number into one massive
 * hardcoded class"): {@link Framework} holds values any future event -- an End invasion, a robot
 * uprising -- would need, and {@link ZombieRaid} holds only what is specific to this one event.
 */
public final class EventConfig {
	private static EventConfig instance = new EventConfig();

	public Framework framework = new Framework();
	public ZombieRaid zombieRaid = new ZombieRaid();
	public SupervillainRaid supervillainRaid = new SupervillainRaid();

	/** Values shared by every event that ever uses {@link EventInstance}. */
	public static final class Framework {
		/** Radius, in blocks, of the event area around its centre. */
		public double eventRadius = 96.0;
		/** Beyond this distance a participant is warned they are leaving. */
		public double warnRadius = 110.0;
		/** Beyond this distance a participant stops counting as present. */
		public double abandonRadius = 160.0;
		/** With nobody present for this long the event pauses (no spawning, no timers). */
		public int pauseAfterEmptyTicks = 30 * 20;
		/** With nobody present for this long the event fails and cleans itself up. */
		public int failAfterEmptyTicks = 5 * 60 * 20;
		/** How often an event's own logic runs. Ten ticks is twice a second -- plenty for wave logic. */
		public int tickIntervalTicks = 10;
		/** Nearest / furthest a wave mob may be spawned from the event centre. */
		public int minSpawnDistance = 14;
		public int maxSpawnDistance = 40;
		/** Never spawn a wave mob closer than this to any player (no spawning on someone's head). */
		public int minSpawnDistanceFromPlayer = 8;
		/** Hard ceiling on event-owned mobs alive at once, whatever the wave table asks for.
		 *  v0.9.9: 90 -> 120 to accommodate the doubled wave sizes. */
		public int maxLiveMobs = 120;
		/** Two events may not be started closer together than this. */
		public int minDistanceBetweenEvents = 256;
	}

	/** Zombie Raid specific tuning. */
	public static final class ZombieRaid {
		// ---- Gravebound Curse ----
		/** Active-play ticks the curse runs before the raid begins. 20 real minutes. */
		public int curseDurationTicks = 20 * 60 * 20;
		/** Ticks left when the curse enters its "final warning" stage. */
		public int curseFinalStageTicks = 2 * 60 * 20;

		// ---- Cursed Zombie ----
		/** Fraction of naturally spawning vanilla zombies that become Cursed Zombies instead. v0.9.3:
		 *  doubled (0.005 -> 0.01) so a player who has already beaten a raid can realistically run into
		 *  another carrier and be cursed again -- the curse/raid has always been repeatable, this just
		 *  makes the natural re-trigger show up often enough to notice. */
		public double cursedZombieChance = 0.01;
		/** The same roll within {@link #graveyardProximityBlocks} of a known Graveyard. v0.9.3: 0.025 -> 0.035. */
		public double cursedZombieChanceNearGraveyard = 0.035;
		/** Radius, in blocks, of the "near a Graveyard" bonus. Checked against a cheap cached list. */
		public int graveyardProximityBlocks = 144;

		// ---- waves ----
		public int waveCount = 12;
		/** Extra mobs per wave for each participant beyond the first, as a fraction of the base count.
		 *  v0.9.9: 0.45 -> 0.30 now that the solo base counts are twice what they were. */
		public double mobCountPerExtraPlayer = 0.30;
		/** Ticks between a wave being cleared and the next one starting. */
		public int betweenWaveTicks = 8 * 20;
		/** Ticks the raid gives a wave before it tops up stragglers that got stuck / despawned. */
		public int waveStallTicks = 45 * 20;

		// ---- bosses ----
		public double bossBaseHealth = 400.0;
		/** Powered Zombie Boss melee damage (waves 4 &amp; 8), and the wave-12 final boss's. v0.9.9. */
		public double bossMeleeDamage = 15.0;
		public double finalBossMeleeDamage = 18.0;
		/** Added per participant for participants 2..4. */
		public double bossHealthPerPlayerTo4 = 200.0;
		/** Added per participant beyond the fourth. */
		public double bossHealthPerPlayerBeyond4 = 150.0;
		/** Multiplier on boss ability cooldowns. Below 1 = more frequent abilities. */
		public double bossCooldownMultiplier = 1.0;
		/** Extra cooldown reduction per participant beyond the first (capped by the field below). */
		public double bossCooldownReductionPerPlayer = 0.06;
		public double bossCooldownReductionCap = 0.30;
		/** The wave-12 boss's multiplier on health, and its own shorter cooldowns. */
		public double finalBossHealthMultiplier = 1.35;
		public double finalBossCooldownMultiplier = 0.7;
		/** Chance the wave-12 boss spawns with a second compatible Experimental Power. */
		public double finalBossDualPowerChance = 0.5;

		// ---- drops ----
		public double graveEssenceFromBasicChance = 0.5;
		public int graveEssenceFromSpecialMin = 1;
		public int graveEssenceFromSpecialMax = 2;
		public int graveEssenceFromJuggernautMin = 2;
		public int graveEssenceFromJuggernautMax = 4;
		public int graveEssenceFromBossMin = 10;
		public int graveEssenceFromBossMax = 20;
		public int graveEssenceFromFinalBossMin = 25;
		public int graveEssenceFromFinalBossMax = 40;
		/** Cursed Zombie's Grave Essence drop chance, and the chance of a bonus second one. */
		public double cursedZombieEssenceChance = 0.33;
		public double cursedZombieBonusEssenceChance = 0.08;
		/** Chance a Powered Zombie Boss also drops its power trophy head. */
		public double bossTrophyChance = 0.25;

		// ---- artifacts ----
		/** Gravewalker Charm: health fraction that arms it, effect length, and its cooldown. */
		public double gravewalkerThreshold = 0.20;
		public int gravewalkerEffectTicks = 5 * 20;
		public int gravewalkerCooldownTicks = 2 * 60 * 20;
		/** Undying Totem charges. */
		public int undyingTotemCharges = 3;
		/** Necrotic Blade: wither chance per hit, stack size / duration of its undead-kill bonus. */
		public double necroticWitherChance = 0.20;
		public int necroticMaxStacks = 5;
		public int necroticStackTicks = 12 * 20;
		public double necroticDamagePerStack = 1.0;
	}

	/**
	 * Supervillain Village Raid tuning. A separate rare superhero-themed village event, triggered by a
	 * Pillager Spy landing a hit on a player inside a village.
	 */
	public static final class SupervillainRaid {
		/**
		 * The Pillager Spy's spawn weight relative to a vanilla Pillager patrol leader. Vanilla patrol
		 * spawning rolls a leader roughly every few minutes of play far from a village; the spy piggy-backs
		 * on that same {@code PatrolSpawner} pass and replaces the would-be patrol about this often -- so
		 * ~5% means "one in twenty patrols is actually a scouting spy". Raise it to see them more often
		 * while testing.
		 */
		public double pillagerSpySpawnChance = 0.05;
		/** Chance the spy is escorted by 1-2 ordinary Pillagers. */
		public double pillagerSpyEscortChance = 0.35;

		/** Seconds between the spy hitting a player in a village and the raid's first wave. */
		public int raidCountdownSeconds = 10 * 60;
		/** Minecraft days a village is immune to another Supervillain Raid after one finishes. */
		public int villageRaidCooldownDays = 3;

		/** Extra wave mobs per participant beyond the first, as a fraction of the base count. */
		public double waveMobMultiplier = 0.5;
		/** Seconds between a wave clearing and the next starting. */
		public int betweenWaveSeconds = 15;
		/** Seconds of ominous quiet between wave 5 clearing and the Supervillain arriving. */
		public int bossArrivalSeconds = 20;

		/** Boss base health, and health added per participant beyond the first. v0.9.10: 350/125 -> 450/150. */
		public double bossBaseHealth = 450.0;
		public double bossHealthPerAdditionalPlayer = 150.0;
		/** Cap on participants that scale the boss health, so a huge server does not make it unkillable. */
		public int bossHealthPlayerCap = 8;
		/** Boss knockback resistance (0..1). v0.9.10: 0.35 -> 0.45, matching the Zombie Raid boss. */
		public double bossKnockbackResistance = 0.45;
		/**
		 * Multiplier applied to boss-power ability damage. v0.9.10: 0.7 -> 1.0 (the boss now hits as hard
		 * with its power as the Zombie Raid boss does -- "make the boss more powerful"). Still fair
		 * because every boss ability is telegraphed and dodgeable (shared {@code BossPowerController}).
		 */
		public double bossAbilityDamageScale = 1.0;
		/**
		 * v0.9.10: the Supervillain's melee (base attack) damage. New key -- lands at this default even
		 * on a config file written before v0.9.10. Was effectively 10 (the entity default) before.
		 */
		public double bossMeleeDamage = 16.0;

		/** Supervillain Token drop chance. */
		public double supervillainTokenDropChance = 0.20;
		/** Power Fragment drop chance (type depends on the boss's power). */
		public double powerFragmentDropChance = 0.12;
		/** Boss trophy (Chimera Core / Arsenal Reactor / Omega Crystal) drop chance. */
		public double bossTrophyChance = 0.35;

		/** Champion of the Village effect duration, in ticks (30 minutes). */
		public int championOfTheVillageTicks = 30 * 60 * 20;

		/**
		 * Model-selection weights for the three villain appearances (Chimera / Arsenal / Omega Mage).
		 * Equal by default = ~33.3% each; the appearance never influences the power.
		 */
		public double weightChimera = 1.0;
		public double weightArsenal = 1.0;
		public double weightOmegaMage = 1.0;

		/**
		 * Power keys the Supervillain may roll, in addition to the boss-capable defaults. Empty = use
		 * exactly the {@code BossPowers} registry (every power that can function on an AI mob).
		 */
		public java.util.List<String> allowedBossPowers = new java.util.ArrayList<>();
		/** Power keys the Supervillain may never roll, even if boss-capable. */
		public java.util.List<String> blockedBossPowers = new java.util.ArrayList<>();
	}

	public static SupervillainRaid supervillain() {
		return instance.supervillainRaid;
	}

	private EventConfig() {
	}

	public static EventConfig get() {
		return instance;
	}

	public static Framework framework() {
		return instance.framework;
	}

	public static ZombieRaid raid() {
		return instance.zombieRaid;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("projecthero_events.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				EventConfig loaded = gson.fromJson(Files.readString(path), EventConfig.class);
				if (loaded != null) {
					instance = loaded;
					if (instance.framework == null) {
						instance.framework = new Framework();
					}
					if (instance.zombieRaid == null) {
						instance.zombieRaid = new ZombieRaid();
					}
					if (instance.supervillainRaid == null) {
						instance.supervillainRaid = new SupervillainRaid();
					}
				}
			}
			// Always (re)write so new keys appear after a mod update, exactly like HeroConfig.
			Files.createDirectories(path.getParent());
			Files.writeString(path, gson.toJson(instance));
		} catch (IOException | JsonSyntaxException e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not load event config, using defaults", e);
			instance = new EventConfig();
		}
	}
}
