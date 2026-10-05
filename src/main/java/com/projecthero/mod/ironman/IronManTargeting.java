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
	public static final double LOCK_CONE = 10.0;
	public static final double KEEP_CONE = 18.0;
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
		if (current != null && angleTo(eye, look, current) <= KEEP_CONE && current.distanceTo(player) <= RANGE && sees(player, current)) {
			return;
		}
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity e : AbilityHelpers.hostilesAround(player, eye, RANGE)) {
			if (!e.isAlive() || e.isInvisible() || e.isSpectator()) {
				continue;
			}
			double angle = angleTo(eye, look, e);
			if (angle > LOCK_CONE || !sees(player, e)) {
				continue;
			}
			double score = angle + e.distanceTo(player) * 0.03;
			if (score < bestScore) {
				bestScore = score;
				best = e;
			}
		}
		if (best != null && best != current) {
			AbilityHelpers.sound(player, SoundEvents.NOTE_BLOCK_BIT.value(), 0.45f, 1.8f);
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
