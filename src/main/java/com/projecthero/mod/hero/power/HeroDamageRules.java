package com.projecthero.mod.hero.power;

import java.util.ArrayList;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * Passive damage rules from experimental powers (fall/fire/explosion/lightning/freeze resistances).
 * One global {@code ALLOW_DAMAGE} listener keyed off the player's <em>active</em> power. Never
 * touches Thor -- {@code ThorPassives.onAllowDamage} is a separate listener.
 *
 * <p>Fabric's {@code ALLOW_DAMAGE} is a boolean veto with no "reduce amount", so partial reductions
 * are done by cancelling the hit and re-applying a smaller one, guarded by a thread-local flag so
 * the re-applied hit passes straight through.
 */
public final class HeroDamageRules {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private HeroDamageRules() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(HeroDamageRules::onAllowDamage);
	}

	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || !(entity instanceof ServerPlayer player)) {
			return true;
		}
		java.util.Set<String> owned = ExperimentalPowers.state(player).ownedPowers;
		if (owned.isEmpty()) {
			return true;
		}

		// v0.9.3 -- persistent power stacking: EVERY owned Experimental Tier power's passive damage rule
		// applies at once, not just the selected one's. Immunities from any power cancel the hit
		// outright; partial reductions from several powers multiply together and are applied in one
		// re-issued hit at the end.
		boolean cancel = false;
		float factor = 1.0f;
		for (String key : new ArrayList<>(owned)) {
			Power active = Powers.byKey(key);
			if (active == null) {
				continue;
			}
			Verdict v = ruleFor(player, active, source, amount);
			if (v.cancel) {
				cancel = true;
			}
			factor *= v.factor;
		}

		if (cancel) {
			return false;
		}
		if (factor < 0.999f) {
			return reduce(player, source, amount, factor);
		}
		return true;
	}

	/** The multiplicative reduction / immunity a single owned power contributes for this hit. */
	private record Verdict(boolean cancel, float factor) {
		static final Verdict NONE = new Verdict(false, 1.0f);

		static Verdict immune() {
			return new Verdict(true, 1.0f);
		}

		static Verdict mult(float f) {
			return new Verdict(false, f);
		}
	}

	private static Verdict ruleFor(ServerPlayer player, Power active, DamageSource source, float amount) {
		boolean fall = source.is(DamageTypeTags.IS_FALL);

		// Sonic Jump / Recoil Jump: a short window of fall immunity after the launch.
		if (fall) {
			long noFallUntil = (long) ExperimentalPowers.getResource(player, active, "no_fall_until");
			if (noFallUntil > player.level().getGameTime()) {
				player.resetFallDistance();
				return Verdict.immune();
			}
		}

		switch (active.key()) {
			case "power_01_super_strength" -> {
				// 65% less fall damage, 20% less explosion damage, 15% off everything else -- and
				// Maximum Effort hardens the body further while it runs.
				if (fall) {
					return Verdict.mult(0.35f);
				}
				float f = 0.85f;
				if (source.is(DamageTypeTags.IS_EXPLOSION)) {
					f *= 0.8f;
				}
				if (com.projecthero.mod.hero.power.p01.SuperStrengthHandlers.maxEffortActive(player)) {
					f *= 0.7f;
				}
				return Verdict.mult(f);
			}
			case "power_03_flight", "power_16_spider_climbing_adhesion" -> {
				if (fall) {
					player.resetFallDistance();
					return Verdict.immune();
				}
			}
			case "power_17_elasticity" -> {
				if (fall) {
					player.resetFallDistance();
					return Verdict.immune();
				}
				// v0.10.10: a rubber body simply does not take an arrow. Elastic Form only ever bounced
				// melee attackers away, which left the form useless against exactly the ranged enemies it
				// most obviously should shrug off.
				if (source.is(DamageTypeTags.IS_PROJECTILE)
						&& com.projecthero.mod.hero.power.p17.ElasticityHandlers.deflectsProjectiles(player)) {
					return Verdict.immune();
				}
				// v0.10.13: Inflated Form soaks half of every hit.
				float elasticFactor = com.projecthero.mod.hero.power.p17.ElasticityHandlers.damageTakenFactor(player);
				if (elasticFactor < 0.999f) {
					return Verdict.mult(elasticFactor);
				}
			}
			case "power_24_wind_manipulation" -> {
				if (fall) {
					return Verdict.mult(0.2f); // v0.10.9: 80% less fall damage (was full immunity)
				}
			}
			case "power_18_density_manipulation" -> {
				// Phasing makes you completely intangible -- nothing but the void or a command can hurt you.
				if (com.projecthero.mod.hero.power.p18.DensityManipulationHandlers.phasing(player)
						&& !source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
					return Verdict.immune();
				}
				// v0.10.13: the density chart's damage-taken column, plus Density Anchor's 60% reduction.
				float densityFactor = com.projecthero.mod.hero.power.p18.DensityManipulationHandlers.damageTakenFactor(player);
				if (densityFactor < 0.999f) {
					return Verdict.mult(densityFactor);
				}
			}
			case "power_27_size_manipulation" -> {
				if (fall) {
					return Verdict.mult(0.35f); // every form takes reduced fall damage
				}
			}
			case "power_22_plant_manipulation_chlorokinesis" -> {
				if (fall && player.level() instanceof net.minecraft.server.level.ServerLevel sl
						&& com.projecthero.mod.hero.power.p22.PlantManipulationHandlers.onNatureGround(sl, player.blockPosition())) {
					player.resetFallDistance();
					return Verdict.immune();
				}
			}
			case "power_05_geokinesis" -> {
				// Earth Swim: while phased into the ground, nothing but the void or a command can hurt you
				// (suffocation, fall, drowning, mobs are all shrugged off).
				long swimUntil = (long) ExperimentalPowers.getResource(player, active, "earthswim_until");
				if (swimUntil > player.level().getGameTime()
						&& !source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
					player.resetFallDistance();
					return Verdict.immune();
				}
			}
			case "power_08_pyrokinesis" -> {
				if (source.is(DamageTypeTags.IS_FIRE)) {
					return Verdict.immune();
				}
			}
			case "power_09_cryokinesis" -> {
				if (source.is(DamageTypes.FREEZE)) {
					return Verdict.immune();
				}
			}
			case "power_11_teleportation" -> {
				if (source.is(DamageTypes.FALL) && amount < 8.0f) {
					player.resetFallDistance();
					return Verdict.immune(); // reduced ender-pearl / short-fall damage
				}
			}
			case "power_12_super_regeneration" -> {
				// Mild passive toughness; lethal hits are handled by the Resurrection ability's
				// totem-style death protection (see SuperRegenerationHandlers).
				if (amount > 2.0f && !fall) {
					return Verdict.mult(0.85f);
				}
			}
			case "power_07_electrokinesis" -> {
				if (source.is(DamageTypes.LIGHTNING_BOLT)) {
					return Verdict.immune(); // v0.10.14: an electrokinetic is untouched by lightning
				}
			}
			case "power_14_sonic_scream" -> {
				// A sonic screamer's own ears and body are tuned out -- sonic booms and resonance barely
				// register.
				if (source.is(DamageTypes.SONIC_BOOM)) {
					return Verdict.mult(0.25f);
				}
			}
			case "power_13_super_durability" -> {
				// Projectile Deflection (held) -- and now Block too -- give total projectile immunity.
				if (source.is(DamageTypeTags.IS_PROJECTILE)
						&& (ExperimentalPowers.getResource(player, active, "deflecting") > 0.5f
								|| ExperimentalPowers.getResource(player, active, "blocking") > 0.5f)) {
					return Verdict.immune();
				}
				// Unbreakable: 100% of all damage for 15s (barring world-removal / commands).
				float unbreakableUntil = ExperimentalPowers.getResource(player, active, "unbreakable_until");
				if (unbreakableUntil > player.level().getGameTime()
						&& !source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
					return Verdict.immune();
				}
				float durability = 0.6f; // passive: 40% less damage from every source
				if (ExperimentalPowers.getResource(player, active, "blocking") > 0.5f && isFrontal(player, source)) {
					durability *= 0.4f; // Block: an additional 60% off the frontal 180-degree arc
				}
				if (ExperimentalPowers.isToggled(player, active, active.ability(AbilitySlot.SLOT_6))) {
					durability *= 0.7f; // Tank Mode: an additional 30%
				}
				if (source.is(DamageTypeTags.IS_EXPLOSION)) {
					durability *= 0.5f;
				}
				if (fall) {
					durability *= 0.5f; // 70% less fall damage overall (0.6 * 0.5)
				}
				return Verdict.mult(durability);
			}
			case "power_10_telekinesis" -> {
				// v0.10.12: a telekinetic catches their own fall -- no fall damage ever -- but the reflex
				// draws on the Psi reserve in proportion to the fall it just absorbed.
				if (fall) {
					com.projecthero.mod.hero.power.p10.TelekinesisHandlers.absorbFall(player, amount);
					player.resetFallDistance();
					return Verdict.immune();
				}
				// v0.10.10: the Telekinetic Barrier stops everything outright while it is up -- but every
				// blow it eats is charged to the Psi meter, so a heavy enough hit collapses the barrier by
				// emptying it (and empty means a 10 s burnout).
				if (!source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)
						&& com.projecthero.mod.hero.power.p10.TelekinesisHandlers.barrierAbsorbs(player, amount)) {
					return Verdict.immune();
				}
			}
			case "power_23_gravity_manipulation" -> {
				if (fall) {
					return Verdict.mult(0.2f);
				}
			}
			case "power_21_shockwave_manipulation" -> {
				if (source.is(DamageTypeTags.IS_EXPLOSION)) {
					return Verdict.mult(0.5f);
				}
			}
			case "power_20_energy_absorption" -> {
				// v0.10.19: an unconditional passive -- half of every hit becomes energy, all the time,
				// unless Overload Release has just locked the meter out. v0.10.20: holding V's
				// Absorption Field boosts that split to 90%.
				if (com.projecthero.mod.hero.power.p20.EnergyAbsorptionHandlers.canAbsorb(player)) {
					float soak = com.projecthero.mod.hero.power.p20.EnergyAbsorptionHandlers.fieldActive(player) ? 0.9f : 0.5f;
					ExperimentalPowers.addResource(player, active, "energy", amount * soak, 500.0f);
					com.projecthero.mod.hero.power.p20.EnergyAbsorptionHandlers.markAbsorbed(player);
					return Verdict.mult(1.0f - soak);
				}
			}
			default -> {
			}
		}
		return Verdict.NONE;
	}

	/** True when the damage originates from within the player's frontal 180-degree arc. */
	private static boolean isFrontal(ServerPlayer player, DamageSource source) {
		net.minecraft.world.phys.Vec3 sp = source.getSourcePosition();
		if (sp == null) {
			return false;
		}
		net.minecraft.world.phys.Vec3 look = player.getLookAngle();
		net.minecraft.world.phys.Vec3 to = sp.subtract(player.getEyePosition());
		return look.x * to.x + look.z * to.z > 0.0;
	}

	private static boolean reduce(ServerPlayer player, DamageSource source, float amount, float factor) {
		float reduced = amount * factor;
		if (reduced < 0.5f) {
			return false;
		}
		REENTRANT.set(true);
		try {
			player.hurt(source, reduced);
		} finally {
			REENTRANT.set(false);
		}
		return false;
	}
}
