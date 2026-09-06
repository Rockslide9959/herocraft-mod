package com.projecthero.mod.event.boss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.boss.power.CryokinesisBoss;
import com.projecthero.mod.event.boss.power.DurabilityBoss;
import com.projecthero.mod.event.boss.power.ElectrokinesisBoss;
import com.projecthero.mod.event.boss.power.FlightBoss;
import com.projecthero.mod.event.boss.power.GeokinesisBoss;
import com.projecthero.mod.event.boss.power.GravityBoss;
import com.projecthero.mod.event.boss.power.LaserVisionBoss;
import com.projecthero.mod.event.boss.power.MagnetismBoss;
import com.projecthero.mod.event.boss.power.PyrokinesisBoss;
import com.projecthero.mod.event.boss.power.ShockwaveBoss;
import com.projecthero.mod.event.boss.power.SonicScreamBoss;
import com.projecthero.mod.event.boss.power.SuperSpeedBoss;
import com.projecthero.mod.event.boss.power.SuperStrengthBoss;
import com.projecthero.mod.event.boss.power.TeleportationBoss;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * Which Experimental Powers an Empowered Zombie may be given, and how to build the AI for each.
 *
 * <h2>Eligibility</h2>
 * Only powers whose fantasy actually translates to an AI-controlled mob are registered here. A power
 * that is fundamentally about a player's own inputs -- Invisibility/Light Manipulation, Spider
 * Climbing, Size Manipulation, Density, Elasticity, Telekinesis' item handling, Healing Factor's
 * revive, Plant/Water/Wind terrain shaping, Energy Absorption's damage-conversion, Crystalkinesis'
 * building, Shadow's stealth -- would either do nothing visible on a mob or need a whole second
 * implementation of a player-facing system to mean anything. Leaving those out is the "only select
 * powers that can realistically function for AI-controlled mobs" rule from the design, applied
 * honestly rather than by registering stubs.
 *
 * <h2>Extensibility</h2>
 * A future power becomes boss-capable with one {@link #register} call and one
 * {@link BossPowerController} subclass. Nothing else in the raid, the boss entity or the reward system
 * needs to know it exists -- the boss bar label, the aura, the trophy and the Corrupted Power Core all
 * read the power's own registry entry.
 */
public final class BossPowers {
	private static final Map<String, Function<EmpoweredZombie, BossPowerController>> FACTORIES = new LinkedHashMap<>();

	private BossPowers() {
	}

	public static void initialize() {
		register(SuperStrengthBoss.POWER_KEY, SuperStrengthBoss::new);
		register(LaserVisionBoss.POWER_KEY, LaserVisionBoss::new);
		register(FlightBoss.POWER_KEY, FlightBoss::new);
		register(SuperSpeedBoss.POWER_KEY, SuperSpeedBoss::new);
		register(GeokinesisBoss.POWER_KEY, GeokinesisBoss::new);
		register(ElectrokinesisBoss.POWER_KEY, ElectrokinesisBoss::new);
		register(PyrokinesisBoss.POWER_KEY, PyrokinesisBoss::new);
		register(CryokinesisBoss.POWER_KEY, CryokinesisBoss::new);
		register(TeleportationBoss.POWER_KEY, TeleportationBoss::new);
		register(DurabilityBoss.POWER_KEY, DurabilityBoss::new);
		register(SonicScreamBoss.POWER_KEY, SonicScreamBoss::new);
		register(ShockwaveBoss.POWER_KEY, ShockwaveBoss::new);
		register(GravityBoss.POWER_KEY, GravityBoss::new);
		register(MagnetismBoss.POWER_KEY, MagnetismBoss::new);

		// A boss power must correspond to a real Experimental Power -- the boss bar, trophy and
		// Corrupted Power Core all name it from the power registry. Fail loudly at load rather than
		// producing an unnamed boss in front of a player.
		for (String key : FACTORIES.keySet()) {
			if (Powers.byKey(key) == null) {
				throw new IllegalStateException("boss power '" + key + "' does not match any Experimental Power");
			}
		}
		ProjectHeroMod.LOGGER.info("[ProjectHero] {} experimental powers are boss-capable", FACTORIES.size());
	}

	public static void register(String powerKey, Function<EmpoweredZombie, BossPowerController> factory) {
		if (FACTORIES.putIfAbsent(powerKey, factory) != null) {
			throw new IllegalStateException("duplicate boss power " + powerKey);
		}
	}

	public static boolean isEligible(String powerKey) {
		return FACTORIES.containsKey(powerKey);
	}

	public static List<String> eligibleKeys() {
		return new ArrayList<>(FACTORIES.keySet());
	}

	/** @return a controller for {@code powerKey} bound to {@code boss}, or {@code null} if ineligible. */
	public static BossPowerController create(String powerKey, EmpoweredZombie boss) {
		Function<EmpoweredZombie, BossPowerController> factory = FACTORIES.get(powerKey);
		return factory == null ? null : factory.apply(boss);
	}

	public static String randomKey(RandomSource random) {
		List<String> keys = eligibleKeys();
		return keys.get(random.nextInt(keys.size()));
	}

	/**
	 * A random boss power for the Supervillain Village Raid, honouring the config allow / block lists
	 * ({@code supervillainAllowedPowers} / {@code supervillainBlockedPowers}). Falls back to the full
	 * boss-capable set if the lists leave nothing.
	 */
	public static String randomSupervillainKey(RandomSource random) {
		com.projecthero.mod.event.EventConfig.SupervillainRaid cfg = com.projecthero.mod.event.EventConfig.supervillain();
		List<String> pool = new ArrayList<>();
		for (String key : FACTORIES.keySet()) {
			if (cfg.blockedBossPowers != null && cfg.blockedBossPowers.contains(key)) {
				continue;
			}
			if (cfg.allowedBossPowers != null && !cfg.allowedBossPowers.isEmpty()
					&& !cfg.allowedBossPowers.contains(key)) {
				continue;
			}
			pool.add(key);
		}
		if (pool.isEmpty()) {
			pool = eligibleKeys();
		}
		return pool.get(random.nextInt(pool.size()));
	}

	/** A second power for a dual-power final boss that the first one is willing to be paired with. */
	public static String randomCompatibleKey(RandomSource random, BossPowerController first) {
		List<String> candidates = new ArrayList<>();
		for (String key : FACTORIES.keySet()) {
			if (first.compatibleWith(key)) {
				candidates.add(key);
			}
		}
		return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
	}

	/** The display name of the Experimental Power behind {@code powerKey}. */
	public static Component displayName(String powerKey) {
		Power power = Powers.byKey(powerKey);
		return power == null ? Component.literal(powerKey) : Component.translatable(power.nameKey());
	}
}
