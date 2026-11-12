package com.projecthero.mod.starfire;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.3 Starfire: her hits, both ways.
 * <ul>
 *   <li><b>Taken</b>: 20% off every hit; no fall damage; <b>survives space</b> -- no drowning, no suffocation in the Galactus orbit
 *   dimension, no freezing; nothing touches her during a Star Dash's i-frames. Every hit that lands builds <b>Fury</b>, and so does a
 *   hit on a squadmate within 16 blocks.</li>
 *   <li><b>Dealt</b>: her bare punches knock back hard (Tamaranean strength). Her moves' hits ({@link Starfire#hit}) don't.</li>
 * </ul>
 * A changed hit is cancelled and re-issued behind a re-entrancy guard, vanilla's difficulty scaling undone first (as the Spin's).
 */
public final class StarfireDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private StarfireDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(StarfireDamage::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(StarfireDamage::afterDamage);
	}

	/** Damage that never touches her: falls, drowning, freezing, suffocating in orbit. Public for the tests. */
	public static boolean immune(ServerPlayer victim, DamageSource source) {
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypeTags.IS_FREEZING)) {
			return true;
		}
		return source.is(DamageTypes.IN_WALL) && com.projecthero.mod.galactus.orbit.GalactusOrbit.inOrbit(victim);
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f || !(entity instanceof ServerPlayer victim) || !Starfire.hasPower(victim)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (immune(victim, source)) {
			if (source.is(DamageTypeTags.IS_FALL)) {
				victim.resetFallDistance();
			}
			return false;
		}
		if (Starfire.dashing(victim)) {
			return false; // Star Dash i-frames
		}
		if (victim.invulnerableTime > 10) {
			return true; // vanilla will ignore this hit anyway
		}
		reissue(victim, source, amount * (1f - StarfireConfig.DAMAGE_RESIST));
		return false;
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float base, float dealt, boolean blocked) {
		if (dealt <= 0f) {
			return;
		}
		// her punches knock back
		if (source.getDirectEntity() instanceof ServerPlayer p && source.getEntity() == p && p != entity && Starfire.hasPower(p)
				&& !Starfire.inMoveHit() && source.is(DamageTypes.PLAYER_ATTACK)) {
			float yaw = p.getYRot() * ((float) Math.PI / 180f);
			entity.knockback(StarfireConfig.PUNCH_KNOCKBACK, Math.sin(yaw), -Math.cos(yaw));
		}
		if (!(entity instanceof ServerPlayer victim)) {
			return;
		}
		// Fury: her own wounds, and a squadmate's within 16 blocks
		if (Starfire.hasPower(victim)) {
			Starfire.addFury(victim, dealt * StarfireConfig.FURY_PER_DAMAGE);
		}
		double r2 = StarfireConfig.SQUAD_RANGE * StarfireConfig.SQUAD_RANGE;
		for (ServerPlayer o : victim.serverLevel().players()) {
			if (o != victim && Starfire.hasPower(o) && o.distanceToSqr(victim) < r2 && com.projecthero.mod.squad.Squads.areAllies(o, victim)) {
				Starfire.addFury(o, dealt * StarfireConfig.FURY_PER_SQUAD_DAMAGE);
			}
		}
	}

	private static void reissue(LivingEntity target, DamageSource source, float amount) {
		if (source.scalesWithDifficulty()) {
			amount = switch (target.level().getDifficulty()) {
				case HARD -> amount / 1.5f;
				case EASY -> amount >= 2f ? 2f * (amount - 1f) : amount;
				default -> amount;
			};
		}
		REENTRANT.set(true);
		try {
			target.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
	}
}
