package com.projecthero.mod.galactus;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.galactus.entity.GalactusMob;
import com.projecthero.mod.galactus.entity.SilverSurferEntity;
import com.projecthero.mod.galactus.entity.SurferBoardEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: who Galactus' side may hurt, and how. Every hit from the Devourer, his Herald (before he turns) and the
 * Punishers goes through {@link #hit}. Targets are living things that are not on the cosmic side: players (survival /
 * adventure), their pets and summons, golems -- never another cosmic unit, never the Surfer once he has turned, never a
 * Silver Surfer's Board.
 */
public final class GalactusCombat {
	private GalactusCombat() {
	}

	/** One of the Devourer's own (Galactus, his parts and hand, the converters, pylons, Punishers, the hostile Herald). */
	public static boolean isCosmic(Entity e) {
		if (e instanceof SilverSurferEntity s) {
			return !s.isAlly();
		}
		return e instanceof GalactusMob;
	}

	public static boolean canTarget(Entity e) {
		if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isDeadOrDying() || e.isSpectator() || e instanceof ArmorStand
				|| e instanceof GalactusMob || e instanceof SurferBoardEntity) {
			return false;
		}
		return !(e instanceof Player p) || !p.getAbilities().invulnerable;
	}

	/** A valid player fighter (survival / adventure, alive). Mock gametest players report creative, so abilities are checked. */
	public static boolean isFighter(Player p) {
		return p.isAlive() && !p.isSpectator() && !p.getAbilities().invulnerable;
	}

	public static boolean hit(ServerLevel level, LivingEntity attacker, LivingEntity target, float amount) {
		DamageSource src = attacker == null ? level.damageSources().magic() : level.damageSources().mobAttack(attacker);
		return hit(level, src, target, amount);
	}

	public static boolean hit(ServerLevel level, DamageSource src, LivingEntity target, float amount) {
		if (!canTarget(target)) {
			return false;
		}
		target.invulnerableTime = 0;
		return target.hurt(src, amount);
	}

	public static Player nearestFighter(ServerLevel level, Vec3 from, double range) {
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

	public static List<Player> fightersNear(ServerLevel level, Vec3 from, double range) {
		List<Player> out = new ArrayList<>();
		for (Player p : level.players()) {
			if (isFighter(p) && p.distanceToSqr(from) <= range * range) {
				out.add(p);
			}
		}
		return out;
	}

	public static List<LivingEntity> targetsNear(ServerLevel level, Vec3 at, double radius) {
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
				e -> canTarget(e) && e.distanceToSqr(at) <= radius * radius);
	}

	/** Every valid target within {@code radius} of the segment {@code a -> b}. */
	public static List<LivingEntity> alongLine(ServerLevel level, Vec3 a, Vec3 b, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		AABB box = new AABB(a, b).inflate(radius + 1.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, GalactusCombat::canTarget)) {
			AABB fat = e.getBoundingBox().inflate(radius);
			if (fat.contains(a) || fat.clip(a, b).isPresent()) {
				out.add(e);
			}
		}
		return out;
	}

	public static boolean clearLine(ServerLevel level, Entity source, Vec3 a, Vec3 b) {
		return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source)).getType() == HitResult.Type.MISS;
	}

	/** The ground height under (x, z), searched from {@code fromY + 6} down at most 24 blocks (loaded chunks only). */
	public static double groundY(ServerLevel level, double x, double fromY, double z) {
		net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
		int bx = net.minecraft.util.Mth.floor(x);
		int bz = net.minecraft.util.Mth.floor(z);
		if (!level.isLoaded(new net.minecraft.core.BlockPos(bx, (int) fromY, bz))) {
			return fromY;
		}
		for (int y = (int) Math.floor(fromY) + 6; y > fromY - 24; y--) {
			p.set(bx, y, bz);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
				return y + 1;
			}
		}
		return fromY;
	}
}
