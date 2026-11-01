package com.projecthero.mod.stand;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Stand hits (v0.17.2). Every Star Platinum move goes through {@link #hit}, so every move follows the mod-wide targeting
 * rules ({@link HeroTargets}: never yourself, never a squadmate or a friendly pet, other players only with PvP on),
 * bosses take at most {@link StandConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health a hit and are never knocked about,
 * and inside a time stop the hit is queued on the frozen victim instead ({@link TimeStop#queue}). Star Finger's bleeding
 * ticks here too.
 */
public final class StandCombat {
	private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.6f, 0.02f, 0.02f), 1.0f);

	/** Bleeding victims: id -> (the Stand user, end time). */
	private record Bleed(UUID owner, long until) {
	}

	private static final Map<UUID, Bleed> BLEEDING = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> STAND_HIT = ThreadLocal.withInitial(() -> false);

	private StandCombat() {
	}

	static void clearSessionState() {
		BLEEDING.clear();
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

	/** A push away from {@code from}, flat, plus {@code lift}. */
	public static Vec3 away(Vec3 from, Entity target, double strength, double lift) {
		Vec3 d = target.position().subtract(from);
		d = new Vec3(d.x, 0, d.z);
		d = d.lengthSqr() < 1.0e-4 ? Vec3.ZERO : d.normalize().scale(strength);
		return d.add(0, lift, 0);
	}

	/** The fist hitting: purple dust and a crit spark (Stand users see the dust; the crit is the victim's). */
	private static void impactFx(ServerPlayer owner, LivingEntity target, boolean landed) {
		ServerLevel level = (ServerLevel) owner.level();
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.6, 0);
		Stands.particles(level, Stands.AURA, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0.02);
		if (landed) {
			Stands.particles(level, ParticleTypes.CRIT, c.x, c.y, c.z, 4, 0.2, 0.2, 0.2, 0.25);
		}
	}

	// ---------------------------------------------------------------- Star Finger's bleeding

	public static void bleed(ServerPlayer owner, LivingEntity target, int ticks) {
		BLEEDING.put(target.getUUID(), new Bleed(owner.getUUID(), target.level().getGameTime() + ticks));
	}

	public static boolean bleeding(LivingEntity target) {
		Bleed b = BLEEDING.get(target.getUUID());
		return b != null && b.until > target.level().getGameTime();
	}

	/** Server tick: one point of bleeding a second (held while time is stopped -- a frozen wound does not bleed). */
	public static void tickBleeding(MinecraftServer server) {
		if (BLEEDING.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Bleed>> it = BLEEDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Bleed> e = it.next();
			LivingEntity victim = null;
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(e.getKey()) instanceof LivingEntity le) {
					victim = le;
					break;
				}
			}
			if (victim == null || !victim.isAlive()) {
				it.remove();
				continue;
			}
			long now = victim.level().getGameTime();
			if (now >= e.getValue().until) {
				it.remove();
				continue;
			}
			if (TimeStop.frozen(victim) || now % 20 != 0) {
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(e.getValue().owner);
			DamageSource src = owner != null ? victim.damageSources().playerAttack(owner) : victim.damageSources().generic();
			victim.invulnerableTime = 0;
			victim.hurt(src, StandConfig.BLEED_DAMAGE);
			Vec3 c = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
			((ServerLevel) victim.level()).sendParticles(BLOOD, c.x, c.y, c.z, 6, 0.2, 0.3, 0.2, 0.0); // the wound is the victim's
		}
	}
}
