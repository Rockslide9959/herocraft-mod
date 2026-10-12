package com.projecthero.mod.frostgiant;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.frostgiant.entity.FrostGiantEntity;
import com.projecthero.mod.frostgiant.entity.IceCasketEntity;
import com.projecthero.mod.frostgiant.entity.RuneBrazierEntity;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: how the Frost Giants hurt things and how things hurt them.
 * <ul>
 *   <li>{@link #canTarget}: anything living that isn't one of Jotunheim's own (creative / spectator players, armour
 *       stands and the dead excepted). Rune Braziers are targets too (the giants stamp them out).</li>
 *   <li>{@link #hit}: every giant hit goes through here (the Warlord's Rally bonus, freeze added on top).</li>
 *   <li>{@link #multiplier}: fire / lava / a burning attacker / Pyrokinesis x1.5; Thor's lightning x1.25.</li>
 * </ul>
 */
public final class FrostCombat {
	public static final String PYROKINESIS = "power_08_pyrokinesis";
	public static final String ELECTROKINESIS = "power_07_electrokinesis";

	private FrostCombat() {
	}

	/** One of Jotunheim's own (a giant, a hound, Laufey, the Casket). */
	public static boolean isFrost(Entity e) {
		return e instanceof FrostGiantEntity || e instanceof IceCasketEntity;
	}

	/** May a Frost Giant attack {@code e}? */
	public static boolean canTarget(Entity e) {
		if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isDeadOrDying() || e.isSpectator() || e instanceof ArmorStand || isFrost(e)) {
			return false;
		}
		if (e instanceof RuneBrazierEntity brazier) {
			return brazier.isLit();
		}
		return !(e instanceof Player p) || !p.getAbilities().invulnerable;
	}

	/** A valid player fighter (survival / adventure, alive). Mock gametest players report creative, so abilities are checked. */
	public static boolean isFighter(Player p) {
		return p.isAlive() && !p.isSpectator() && !p.getAbilities().invulnerable;
	}

	/** Hurts {@code target} from {@code attacker} for {@code amount} (+ the Rally bonus) and adds {@code freezeTicks}. */
	public static boolean hit(ServerLevel level, LivingEntity attacker, LivingEntity target, float amount, int freezeTicks) {
		DamageSource src = attacker == null ? level.damageSources().freeze() : level.damageSources().mobAttack(attacker);
		return hit(level, src, attacker, target, amount, freezeTicks);
	}

	public static boolean hit(ServerLevel level, DamageSource src, LivingEntity attacker, LivingEntity target, float amount, int freezeTicks) {
		if (!canTarget(target)) {
			return false;
		}
		if (attacker instanceof FrostGiantEntity g && g.isRallied()) {
			amount *= (float) (1.0 + FrostGiantConfig.warlord().rallyDamageBonus);
		}
		target.invulnerableTime = 0;
		boolean hurt = target.hurt(src, amount);
		if (hurt && freezeTicks > 0) {
			freeze(target, freezeTicks);
		}
		return hurt;
	}

	/** Adds {@code ticks} of frost to {@code e} (capped a little above full frost so it reads as "frozen solid"). */
	public static void freeze(LivingEntity e, int ticks) {
		if (!e.canFreeze() || ticks <= 0) {
			return;
		}
		int cap = e.getTicksRequiredToFreeze() + 60;
		e.setTicksFrozen(Math.min(cap, e.getTicksFrozen() + ticks));
	}

	// ---------------------------------------------------------------- damage taken

	/**
	 * The multiplier a Frost Giant applies to {@code amount} from {@code source}: fire / lava / a burning attacker /
	 * Pyrokinesis {@link FrostGiantConfig.Damage#fireMultiplier}; lightning or Electrokinesis
	 * {@link FrostGiantConfig.Damage#lightningMultiplier}. The bigger applies (they don't stack).
	 */
	public static float multiplier(DamageSource source) {
		FrostGiantConfig.Damage cfg = FrostGiantConfig.damage();
		if (isFire(source)) {
			return (float) cfg.fireMultiplier;
		}
		if (isLightning(source)) {
			return (float) cfg.lightningMultiplier;
		}
		return 1.0f;
	}

	public static boolean isFire(DamageSource source) {
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return true;
		}
		Entity attacker = source.getEntity();
		if (attacker != null && attacker.isOnFire()) {
			return true;
		}
		if (source.getDirectEntity() instanceof Projectile proj && proj.isOnFire()) {
			return true;
		}
		return attacker instanceof Player p && hasActivePower(p, PYROKINESIS);
	}

	/** Lightning (Thor's storms, a struck bolt) or a hit from someone wielding Electrokinesis. */
	public static boolean isLightning(DamageSource source) {
		if (source.is(DamageTypeTags.IS_LIGHTNING)) {
			return true;
		}
		return source.getEntity() instanceof Player p && hasActivePower(p, ELECTROKINESIS);
	}

	public static boolean hasActivePower(Player p, String key) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && key.equals(st.activePower);
	}

	// ---------------------------------------------------------------- finding things

	/** The nearest valid player fighter within {@code range} of {@code from}, or null. */
	public static Player nearestPlayer(ServerLevel level, Vec3 from, double range) {
		Player best = null;
		double bestD = range * range;
		for (Player p : level.players()) {
			if (!isFighter(p)) {
				continue;
			}
			double d = p.distanceToSqr(from);
			if (d <= bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	/** Every valid player fighter within {@code range} of {@code from}. */
	public static List<Player> playersNear(ServerLevel level, Vec3 from, double range) {
		List<Player> out = new ArrayList<>();
		for (Player p : level.players()) {
			if (isFighter(p) && p.distanceToSqr(from) <= range * range) {
				out.add(p);
			}
		}
		return out;
	}

	/** Every valid target within {@code radius} of {@code at}. */
	public static List<LivingEntity> targetsNear(ServerLevel level, Vec3 at, double radius) {
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
				e -> canTarget(e) && e.distanceToSqr(at) <= radius * radius);
	}

	/** Every valid target within {@code radius} of the segment {@code a -> b}. */
	public static List<LivingEntity> alongLine(ServerLevel level, Vec3 a, Vec3 b, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		AABB box = new AABB(a, b).inflate(radius + 1.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, FrostCombat::canTarget)) {
			AABB fat = e.getBoundingBox().inflate(radius);
			if (fat.contains(a) || fat.clip(a, b).isPresent()) {
				out.add(e);
			}
		}
		return out;
	}

	/**
	 * Every living thing (targets and braziers both, via {@code canTarget}) in the cone from {@code origin} along {@code dir},
	 * within {@code range} and {@code halfAngleDeg}, with a clear line (blocks -- ice walls, pillars -- stop it).
	 */
	public static List<LivingEntity> inCone(ServerLevel level, Entity source, Vec3 origin, Vec3 dir, double range, double halfAngleDeg) {
		return inCone(level, source, origin, dir, range, halfAngleDeg, FrostCombat::canTarget);
	}

	/** {@link #inCone} with its own filter (the Casket item hits giants too). */
	public static List<LivingEntity> inCone(ServerLevel level, Entity source, Vec3 origin, Vec3 dir, double range, double halfAngleDeg,
			java.util.function.Predicate<LivingEntity> filter) {
		List<LivingEntity> out = new ArrayList<>();
		Vec3 d = dir.normalize();
		double cos = Math.cos(Math.toRadians(halfAngleDeg));
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin).inflate(range), filter)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(origin);
			double len = to.length();
			if (len > range || len < 1.0e-3 || to.scale(1.0 / len).dot(d) < cos) {
				continue;
			}
			if (clearLine(level, source, origin, e.getBoundingBox().getCenter())) {
				out.add(e);
			}
		}
		return out;
	}

	/** No block between {@code a} and {@code b}. */
	public static boolean clearLine(ServerLevel level, Entity source, Vec3 a, Vec3 b) {
		return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source)).getType() == HitResult.Type.MISS;
	}
}
