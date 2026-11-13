package com.projecthero.mod.raven;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;

/**
 * v0.17.3 Raven: the hits she takes (revamp). Nothing touches her while she is a streak of shadow (Shadow Dash, sunk for Shadow
 * Step); the Soul Shield soaks hits first; what gets through is 10% lighter, 30% more for magic and the supernatural (Demonic
 * Heritage), and lighter again inside an Astral Sanctuary. A squadmate standing in her sanctuary takes lighter hits too. Every hit
 * that lands pushes her Composure down, stops a healing channel and can break a Soul Detonation. A changed hit is cancelled and
 * re-issued behind a re-entrancy guard, vanilla's difficulty scaling undone first.
 */
public final class RavenDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private RavenDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(RavenDamage::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, dealt, blocked) -> {
			if (dealt > 0f && entity instanceof ServerPlayer p && Raven.hasPower(p)) {
				Raven.onHurt(p, dealt);
				RavenSpells.onHurt(p, dealt);
			}
		});
	}

	/** Demonic Heritage: magic and the supernatural (potions, evoker fangs, witches, vexes, the Warden's boom, wither, lightning). */
	public static boolean supernatural(DamageSource source) {
		if (source.is(DamageTypeTags.WITCH_RESISTANT_TO) || source.is(DamageTypes.LIGHTNING_BOLT) || source.is(DamageTypes.WITHER)
				|| source.is(DamageTypes.WITHER_SKULL) || source.is(DamageTypes.DRAGON_BREATH) || source.is(DamageTypes.SONIC_BOOM)) {
			return true;
		}
		Entity by = source.getEntity();
		return by instanceof Witch || by instanceof Evoker || by instanceof Vex;
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f || !(entity instanceof ServerPlayer victim)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (!Raven.hasPower(victim)) {
			// a squadmate in a Raven's Astral Sanctuary
			float r = RavenSpells.sanctuaryReduction(victim);
			if (r <= 0f || victim.invulnerableTime > 10) {
				return true;
			}
			reissue(victim, source, amount * (1f - r), true);
			return false;
		}
		if (Raven.inShadow(victim)) {
			return false;
		}
		if (victim.invulnerableTime > 10) {
			return true; // vanilla will ignore this hit anyway
		}
		float left = amount;
		if (Raven.guarding(victim) && !source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypeTags.IS_DROWNING)
				&& !source.is(DamageTypes.STARVE)) {
			left = RavenMoves.guardAbsorb(victim, source, amount);
			if (left <= 0f) {
				return false;
			}
		}
		float mult = 1f - RavenConfig.DAMAGE_RESIST;
		if (supernatural(source)) {
			mult *= 1f - RavenConfig.HERITAGE_RESIST;
		}
		mult *= 1f - RavenSpells.sanctuaryReduction(victim);
		reissue(victim, source, left * mult, true);
		return false;
	}

	/** {@code amount} with vanilla's difficulty scaling taken off (what the hit is really worth). */
	private static float scaled(LivingEntity target, DamageSource source, float amount) {
		if (source.scalesWithDifficulty()) {
			return switch (target.level().getDifficulty()) {
				case HARD -> amount / 1.5f;
				case EASY -> amount >= 2f ? 2f * (amount - 1f) : amount;
				default -> amount;
			};
		}
		return amount;
	}

	private static void reissue(LivingEntity target, DamageSource source, float amount, boolean unscale) {
		if (unscale) {
			amount = scaled(target, source, amount);
		}
		REENTRANT.set(true);
		try {
			target.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
	}
}
