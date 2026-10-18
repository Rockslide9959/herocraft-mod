package com.projecthero.mod.quirk.zerogravity;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.squad.Squads;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.7: Zero Gravity's shared hit code, targeting and her pastel-pink look. Every hit goes through {@link #strike}:
 * the mod-wide rules ({@link HeroTargets#canHarm}), never a squadmate ({@link Squads#areAllies}, even with friendly
 * fire on), bosses capped at {@link ZeroGravityConfig#BOSS_MAX_FRACTION_PER_HIT} a hit and never knocked about.
 */
public final class ZeroGravityCombat {
	public static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1.0f, 0.56f, 0.78f), 1.0f);
	public static final DustParticleOptions PINK_BIG = new DustParticleOptions(new Vector3f(1.0f, 0.66f, 0.84f), 1.6f);
	public static final DustParticleOptions PINK_PALE = new DustParticleOptions(new Vector3f(1.0f, 0.86f, 0.93f), 0.8f);

	private ZeroGravityCombat() {
	}

	public static boolean isBoss(LivingEntity e) {
		return TitanCombat.isBoss(e);
	}

	/** May she hurt {@code e} (and float it with an area move)? Never a squadmate, never herself. */
	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return e != owner && HeroTargets.canHarm(owner, e) && !Squads.areAllies(owner, e);
	}

	/** One hit: damage (boss-capped), knockback away from {@code origin}, {@code lift}. True if it landed. */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * ZeroGravityConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
		if (!AbilityHelpers.hurtBurst(owner, target, owner.level().damageSources().playerAttack(owner), Math.max(0.5f, dmg))) {
			return false;
		}
		if (!boss) {
			if (knockback > 0.0) {
				AbilityHelpers.knockbackFrom(target, origin, knockback);
			}
			if (lift > 0.0) {
				AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
			}
		}
		Vec3 c = center(target);
		owner.serverLevel().sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 5, 0.25, 0.3, 0.25, 0.2);
		return true;
	}

	public static Vec3 center(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.55, 0);
	}

	/** The creature under the crosshair, else the one nearest the crosshair within {@code degrees} she can see. */
	public static @Nullable LivingEntity aimed(ServerPlayer p, double range, double degrees, java.util.function.Predicate<LivingEntity> ok) {
		LivingEntity direct = AbilityHelpers.raycastEntity(p, range);
		if (direct != null && ok.test(direct)) {
			return direct;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(degrees));
		LivingEntity best = null;
		for (LivingEntity le : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
				e -> e != p && e.isAlive() && ok.test(e))) {
			Vec3 to = le.position().add(0, le.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d < 0.3 || d > range + le.getBbWidth()) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot && p.hasLineOfSight(le)) {
				bestDot = dot;
				best = le;
			}
		}
		return best;
	}

	/** The five-finger touch: pink sparkles where her fingertips met it, and the soft "boop". */
	public static void touchFx(ServerLevel level, Vec3 at) {
		level.sendParticles(PINK_BIG, at.x, at.y, at.z, 10, 0.25, 0.3, 0.25, 0.0);
		level.sendParticles(PINK_PALE, at.x, at.y, at.z, 8, 0.4, 0.4, 0.4, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 3, 0.2, 0.2, 0.2, 0.03);
		level.sendParticles(ParticleTypes.WAX_OFF, at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.5);
		boop(level, at, 1.0f);
	}

	public static void boop(ServerLevel level, Vec3 at, float pitch) {
		level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6f, 1.6f * pitch);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.5f * pitch);
	}

	/** A soft pastel trail under a floater. */
	public static void trail(ServerLevel level, Vec3 at) {
		level.sendParticles(PINK, at.x, at.y, at.z, 1, 0.15, 0.05, 0.15, 0.0);
	}

	public static void burst(ServerLevel level, Vec3 c, int count, double spread) {
		level.sendParticles(PINK_BIG, c.x, c.y, c.z, count, spread, spread, spread, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, Math.max(1, count / 5), spread, spread, spread, 0.03);
	}
}
