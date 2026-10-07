package com.projecthero.mod.ironman;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.network.IronManLockPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.26: the Mark III's targeting system -- Max Steel style lock-on. While the helmet is on with the visor closed, it
 * locks the hostile nearest the crosshair (inside {@link #LOCK_CONE} degrees, in line of sight, within {@link #RANGE}
 * blocks) and holds it out to {@link #KEEP_CONE}. Locked, the suit <b>auto-aims</b>: repulsor blasts and the Unibeam go
 * straight to the target, rockets fly at it and micro missiles home onto it. The wearer's client is told the lock
 * ({@link IronManLockPayload}) to draw the reticle and the target panel.
 *
 * <p>The lock map is static server state (entity ids only) and is cleared in {@code ServerStateReset}.
 */
public final class IronManTargeting {
	public static final double RANGE = 100.0;
	/** v0.14.28: tighter (was 10 / 18) -- the lock follows the crosshair so one creature in a group can be picked out. */
	public static final double LOCK_CONE = 5.0;
	public static final double KEEP_CONE = 7.0;
	/** v0.14.28: switch to another candidate once it sits this many degrees nearer the crosshair than the current lock. */
	public static final double SWITCH_MARGIN = 1.0;
	private static final Map<UUID, Integer> LOCK = new HashMap<>();

	private IronManTargeting() {
	}

	public static void clearSessionState() {
		LOCK.clear();
	}

	/** Suits with the targeting system. */
	public static boolean hasTargeting(IronManSuit suit) {
		return suit != null && (suit.targeting() || "mark_iii".equals(suit.id())); // v0.14.27: + Mark 2
	}

	/** Every tick for an Iron Man wearer. */
	public static void tick(ServerPlayer player, IronManSuit suit) {
		boolean active = hasTargeting(suit) && IronManArmor.hasHelmet(player, suit.id())
				&& !player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
		if (!active) {
			setLock(player, null);
			return;
		}
		if (player.tickCount % 2 != 0) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		LivingEntity current = locked(player);
		// v0.14.28: whatever is directly under the crosshair always wins -- no more being stuck on a neighbour in a group
		LivingEntity direct = underCrosshair(player, eye, look);
		if (direct != null) {
			if (direct != current) {
				IronManSounds.play(player, IronManSounds.TARGET_LOCK, 0.6f, 1.0f);
			}
			setLock(player, direct);
			return;
		}
		double currentAngle = current != null && current.distanceTo(player) <= RANGE && sees(player, current)
				? angleTo(eye, look, current) : Double.MAX_VALUE;
		if (currentAngle > KEEP_CONE) {
			current = null;
			currentAngle = Double.MAX_VALUE;
		}
		LivingEntity best = null;
		double bestAngle = Double.MAX_VALUE;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity e : AbilityHelpers.hostilesAround(player, eye, RANGE)) {
			if (!e.isAlive() || e.isInvisible() || e.isSpectator()) {
				continue;
			}
			double angle = angleTo(eye, look, e);
			if (angle > LOCK_CONE || !sees(player, e)) {
				continue;
			}
			double score = angle + e.distanceTo(player) * 0.01;
			if (score < bestScore) {
				bestScore = score;
				bestAngle = angle;
				best = e;
			}
		}
		// keep the current lock unless another candidate is clearly nearer the crosshair
		if (current != null && (best == null || best == current || bestAngle > currentAngle - SWITCH_MARGIN)) {
			return;
		}
		if (best != null && best != current) {
			IronManSounds.play(player, IronManSounds.TARGET_LOCK, 0.6f, 1.0f);
		}
		setLock(player, best);
	}

	/** The current lock, if it still lives. */
	public static LivingEntity locked(ServerPlayer player) {
		Integer id = LOCK.get(player.getUUID());
		if (id == null) {
			return null;
		}
		Entity e = player.level().getEntity(id);
		return e instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	/** The lock if it is within {@code range} of the player (for weapons with a shorter reach). */
	public static LivingEntity lockedWithin(ServerPlayer player, double range) {
		LivingEntity l = locked(player);
		return l != null && l.distanceTo(player) <= range ? l : null;
	}

	/** Auto-aim: the direction from {@code from} to the lock, or {@code fallback} with no lock in range. */
	public static Vec3 aim(ServerPlayer player, Vec3 from, Vec3 fallback, double range) {
		LivingEntity l = lockedWithin(player, range);
		if (l == null) {
			return fallback;
		}
		Vec3 to = l.position().add(0, l.getBbHeight() * 0.5, 0).subtract(from);
		return to.lengthSqr() < 1.0e-6 ? fallback : to.normalize();
	}

	/**
	 * v0.14.30: the direction every aimed ability fires in -- straight from the eyes at the locked target when there is
	 * one within {@code range}, otherwise along the crosshair. Explicit user request: "the player's abilities are
	 * automatically aimed at whatever the player is currently targeting".
	 */
	public static Vec3 aimLook(ServerPlayer player, double range) {
		return aim(player, player.getEyePosition(), player.getLookAngle().normalize(), range);
	}

	private static void setLock(ServerPlayer player, LivingEntity target) {
		Integer before = LOCK.get(player.getUUID());
		int now = target == null ? -1 : target.getId();
		if (target == null) {
			LOCK.remove(player.getUUID());
		} else {
			LOCK.put(player.getUUID(), now);
		}
		if ((before == null ? -1 : before) != now) {
			ServerPlayNetworking.send(player, new IronManLockPayload(now));
		}
	}

	/** v0.14.28: the living, harmable-looking entity the crosshair ray actually hits first (blocks stop the ray). */
	private static LivingEntity underCrosshair(ServerPlayer player, Vec3 eye, Vec3 look) {
		Vec3 end = eye.add(look.scale(RANGE));
		net.minecraft.world.phys.BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end,
				new net.minecraft.world.phys.AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && !e.isInvisible() && e != player
						&& !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
						&& com.projecthero.mod.combat.HeroTargets.canHarm(player, le),
				eye.distanceToSqr(end));
		return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
	}

	private static double angleTo(Vec3 eye, Vec3 look, Entity e) {
		Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
		double len = to.length();
		if (len < 1.0e-4) {
			return 0.0;
		}
		return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, to.scale(1.0 / len).dot(look)))));
	}

	private static boolean sees(ServerPlayer player, Entity e) {
		return player.level().clip(new ClipContext(player.getEyePosition(), e.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
	}
}
