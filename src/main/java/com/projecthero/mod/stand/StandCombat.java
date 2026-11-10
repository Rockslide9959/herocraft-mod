package com.projecthero.mod.stand;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Stand hits (v0.17.2). Every Star Platinum move goes through {@link #hit}, so every move follows the mod-wide targeting
 * rules ({@link HeroTargets}: never yourself, never a squadmate or a friendly pet, other players only with PvP on),
 * bosses take at most {@link StandConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health a hit and are never knocked about,
 * and inside a time stop the hit is queued on the frozen victim instead ({@link TimeStop#queue}). v0.17.3: Star Finger no
 * longer bleeds (the user set its damage at 22, flat); {@link #stun} holds a victim in place.
 */
public final class StandCombat {
	private static final ThreadLocal<Boolean> STAND_HIT = ThreadLocal.withInitial(() -> false);

	private StandCombat() {
	}

	static void clearSessionState() {
	}

	public static boolean isStandHit() {
		return STAND_HIT.get();
	}

	public static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= StandConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(target);
	}

	public static boolean isTarget(ServerPlayer owner, Entity e) {
		return e instanceof LivingEntity && e.isAlive() && HeroTargets.canHarm(owner, e);
	}

	/**
	 * One Stand hit: damage (boss-capped) and a push of {@code knock} (velocity added; nothing on a boss). Lands through a
	 * recent hit's invulnerability window on mobs. In a time stop it is queued. True if it landed (or was queued).
	 */
	public static boolean hit(ServerPlayer owner, LivingEntity target, float damage, Vec3 knock) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = damage;
		if (boss) {
			dmg = Math.min(dmg, target.getMaxHealth() * StandConfig.BOSS_MAX_FRACTION_PER_HIT);
		}
		Stands.markCombat(owner);
		if (TimeStop.shouldQueue(owner, target)) {
			TimeStop.queue(target, dmg, boss ? Vec3.ZERO : knock);
			impactFx(owner, target, false);
			return true;
		}
		boolean landed;
		STAND_HIT.set(true);
		try {
			DamageSource src = owner.level().damageSources().playerAttack(owner);
			landed = AbilityHelpers.hurtBurst(owner, target, src, Math.max(0.5f, dmg));
		} finally {
			STAND_HIT.set(false);
		}
		if (!landed) {
			return false;
		}
		if (!boss && knock != null && knock.lengthSqr() > 1.0e-5) {
			AbilityHelpers.push(target, knock);
		}
		impactFx(owner, target, true);
		return true;
	}

	/**
	 * v0.17.3 (user: "stunned in place"): {@code ticks} unable to move or fight back (the Punisher's stun: Slowness X, no target,
	 * Weakness); a boss only gets a heavy slow, as with every other kit.
	 */
	public static void stun(LivingEntity e, int ticks) {
		if (isBoss(e)) {
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, ticks, 2);
		} else {
			com.projecthero.mod.punisher.PunisherControl.stun(e, ticks);
		}
	}

	/** A push away from {@code from}, flat, plus {@code lift}. */
	public static Vec3 away(Vec3 from, Entity target, double strength, double lift) {
		Vec3 d = target.position().subtract(from);
		d = new Vec3(d.x, 0, d.z);
		d = d.lengthSqr() < 1.0e-4 ? Vec3.ZERO : d.normalize().scale(strength);
		return d.add(0, lift, 0);
	}

	/** The fist hitting: a flash and shockwave ring (Stand users see it) and a crit spark (the victim's). */
	private static void impactFx(ServerPlayer owner, LivingEntity target, boolean landed) {
		ServerLevel level = (ServerLevel) owner.level();
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.6, 0);
		Stands.vfx(level, com.projecthero.mod.stand.network.StandFxPayload.IMPACT, c, c.subtract(owner.position().add(0, 1.2, 0)), landed ? 0.8f : 0.5f);
		if (landed) {
			Stands.particles(level, ParticleTypes.CRIT, c.x, c.y, c.z, 4, 0.2, 0.2, 0.2, 0.25);
		}
	}
}
