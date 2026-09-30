package com.projecthero.mod.moonknight;

import java.util.function.Predicate;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.4: the Grapple Kick's forgiving aim. Side-neutral (plain {@link Player}), so the server that decides and the
 * owner's client that previews the lock ({@code MoonKnightKickPreviewClient}) run exactly the same selection:
 * <ol>
 *   <li>whatever living thing the crosshair ray hits first (blocks stop the ray), if it is allowed;</li>
 *   <li>otherwise the best allowed living target within {@link MoonKnightConfig#KICK_AIM_CONE_DEGREES} of the
 *       crosshair, up to {@code range}, in line of sight -- scored by how far off the crosshair it is (its body's
 *       angular size taken off, so a big mob is easy to catch), with distance only breaking near-ties.</li>
 * </ol>
 */
public final class MoonKnightAim {
	private MoonKnightAim() {
	}

	public static LivingEntity kickTarget(Player player, double range, Predicate<LivingEntity> allowed) {
		return coneTarget(player, range, MoonKnightConfig.KICK_AIM_CONE_DEGREES, allowed);
	}

	public static LivingEntity coneTarget(Player player, double range, double coneDegrees, Predicate<LivingEntity> allowed) {
		Predicate<LivingEntity> ok = e -> e != player && e.isAlive() && !(e instanceof ArmorStand) && !e.isSpectator()
				&& allowed.test(e);
		HitResult ray = ProjectileUtil.getHitResultOnViewVector(player,
				e -> e instanceof LivingEntity le && e.isPickable() && ok.test(le), range);
		if (ray instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity direct) {
			return direct;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f).normalize();
		double coneRad = Math.toRadians(coneDegrees);
		double spread = range * Math.tan(coneRad) + 2.0;
		AABB box = new AABB(eye, eye.add(look.scale(range))).inflate(spread);
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box, ok)) {
			Vec3 centre = e.getBoundingBox().getCenter();
			Vec3 to = centre.subtract(eye);
			double dist = to.length();
			if (dist < 1.0e-3 || dist > range + e.getBbWidth()) {
				continue;
			}
			double angle = Math.acos(Mth.clamp(to.scale(1.0 / dist).dot(look), -1.0, 1.0));
			// take the body's own angular radius off, so the edge of a wide mob counts as "on" the crosshair
			double body = Math.atan2(Math.max(e.getBbWidth(), e.getBbHeight()) * 0.5, dist);
			double off = Math.max(0.0, angle - body);
			if (off > coneRad || !hasLineOfSight(player, e)) {
				continue;
			}
			double score = Math.toDegrees(off) + dist / range; // the angle decides; distance breaks near-ties (<= 1 deg)
			if (score < bestScore) {
				bestScore = score;
				best = e;
			}
		}
		return best;
	}

	private static boolean hasLineOfSight(Player player, Entity e) {
		return player.hasLineOfSight(e);
	}
}
