package com.projecthero.mod.hero.power.p25;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 hold R -- Water Dragon: a serpent of water rises out of the caster's hands, arcs up over his head and then
 * dives on the target he aimed at (up to {@link WaterHandlers#DRAGON_RANGE} blocks), homing on it as it flies. It rams
 * for {@link WaterHandlers#DRAGON_DAMAGE} and a big knockback (bosses take the hit but are not thrown); anything else
 * within {@link #SPLASH_RADIUS} of the impact takes half. With no creature aimed at it dives on the aimed spot and
 * splashes there. Fire is put out where it lands.
 *
 * <p>The client draws the segmented, rippling body ({@code WaterFxRenderer}): this entity is the head, and the body is
 * the trail of the head's own recent positions, which the client records each tick ({@link #trail}). The synced size is
 * 1 while it flies; once it hits it carries {@code 1000 + the tick it hit at} and the body collapses into spray.
 */
public class WaterDragonEntity extends WaterFxEntity {
	public static final int RISE = 7;
	public static final int LIFE = 46;
	public static final int COLLAPSE = 10;
	public static final double SPEED = 1.5;
	public static final double SPLASH_RADIUS = 2.5;
	/** How many trail points the client keeps (the body's length in ticks of flight). */
	public static final int TRAIL = 16;

	private Vec3 vel = Vec3.ZERO;
	private Vec3 aimPoint;
	private int hitAt = -1;
	private final Set<Integer> hit = new HashSet<>();

	/** Client: the head's last {@link #TRAIL} positions, newest first (filled each client tick). */
	public final Vec3[] trail = new Vec3[TRAIL];
	public int trailCount;

	public WaterDragonEntity(EntityType<? extends WaterDragonEntity> type, Level level) {
		super(type, level);
	}

	/** Sends the dragon from {@code owner}'s hands at {@code target} (or, if null, at {@code point}). */
	public static WaterDragonEntity summon(ServerPlayer owner, LivingEntity target, Vec3 point) {
		ServerLevel level = AbilityHelpers.level(owner);
		WaterDragonEntity e = new WaterDragonEntity(WaterEntities.WATER_DRAGON, level);
		e.setup(owner, target, 1.0f);
		e.aimPoint = point;
		Vec3 look = owner.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 at = owner.position().add(0, 1.2, 0).add(flat.scale(0.7));
		e.setPos(at.x, at.y, at.z);
		// it rises first: up and a little forward
		e.vel = flat.scale(0.45).add(0, 0.55, 0);
		level.addFreshEntity(e);
		return e;
	}

	/** Ticks since it hit, or -1 while it still flies. Either side. */
	public int hitFor() {
		float s = size();
		return s >= 999.5f ? Math.max(0, tickCount - (int) (s - 1000.0f)) : -1;
	}

	@Override
	public void tick() {
		Vec3 before = position();
		super.tick();
		if (level().isClientSide()) {
			if (hitFor() < 0) {
				System.arraycopy(trail, 0, trail, 1, TRAIL - 1);
				trail[0] = before;
				trailCount = Math.min(TRAIL, trailCount + 1);
			}
			return;
		}
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = owner();
		if (hitAt >= 0) {
			if (tickCount - hitAt >= COLLAPSE) {
				discard();
			}
			return;
		}
		if (owner == null || tickCount > LIFE) {
			strike(level, owner, null);
			return;
		}
		LivingEntity t = target();
		Vec3 goal = t != null ? mid(t) : aimPoint;
		if (tickCount > RISE && goal != null) {
			// dive on the goal, homing harder the longer it flies
			Vec3 want = goal.subtract(position());
			double d = want.length();
			if (d > 1.0e-3) {
				double turn = Math.min(0.6, 0.22 + (tickCount - RISE) * 0.03);
				Vec3 dir = vel.normalize().lerp(want.scale(1.0 / d), turn).normalize();
				vel = dir.scale(Math.min(SPEED, vel.length() + 0.18));
			}
		} else {
			vel = vel.add(0, -0.05, 0).scale(1.04); // the rise bends over into its arc
		}
		Vec3 next = position().add(vel);
		// a wall or the floor in the way: it breaks against it
		BlockHitResult wall = level.clip(new ClipContext(position(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (tickCount > 2 && wall.getType() == HitResult.Type.BLOCK) {
			setPos(wall.getLocation().x, wall.getLocation().y, wall.getLocation().z);
			strike(level, owner, null);
			return;
		}
		setPos(next.x, next.y, next.z);
		setYRot((float) (Mth.atan2(vel.z, vel.x) * Mth.RAD_TO_DEG) - 90.0f);
		// the ram: its target, or the first thing in its path
		if (t != null && AbilityHelpers.distanceSqToBox(t, position()) < 1.3 * 1.3) {
			strike(level, owner, t);
			return;
		}
		if (tickCount > RISE) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(owner, position(), 1.1)) {
				if (!Squads.areAllies(owner, e)) {
					strike(level, owner, e);
					return;
				}
			}
		}
		if (t == null && aimPoint != null && position().distanceTo(aimPoint) < 1.2) {
			strike(level, owner, null);
			return;
		}
		if (tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.FALLING_WATER, getX(), getY(), getZ(), 3, 0.3, 0.3, 0.3, 0.0);
		}
		if (tickCount % 6 == 1) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 0.9f, 0.6f);
		}
	}

	/**
	 * The impact: {@code rammed} (if any) takes the full {@link WaterHandlers#DRAGON_DAMAGE} and is thrown; everything
	 * else in the splash takes half. Public for the gametests. Returns how many it hit.
	 */
	public int strike(ServerLevel level, ServerPlayer owner, LivingEntity rammed) {
		if (hitAt >= 0) {
			return 0;
		}
		hitAt = tickCount;
		setSize(1000.0f + tickCount);
		int n = 0;
		Vec3 dir = vel.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, getYRot()) : vel.normalize();
		if (owner != null) {
			if (rammed != null && HeroTargets.canHarm(owner, rammed) && !Squads.areAllies(owner, rammed) && hit.add(rammed.getId())) {
				ram(owner, rammed, WaterHandlers.DRAGON_DAMAGE, dir, 1.0);
				n++;
			}
			for (LivingEntity e : AbilityHelpers.enemiesAround(owner, position(), SPLASH_RADIUS)) {
				if (!Squads.areAllies(owner, e) && hit.add(e.getId())) {
					ram(owner, e, WaterHandlers.DRAGON_DAMAGE * 0.5f, e.position().subtract(position()).add(dir).normalize(), 0.55);
					n++;
				}
			}
		}
		WaterHandlers.douse(level, BlockPos.containing(position()), 2);
		level.sendParticles(ParticleTypes.SPLASH, getX(), getY(), getZ(), 90, 1.2, 0.8, 1.2, 0.4);
		level.sendParticles(ParticleTypes.FALLING_WATER, getX(), getY() + 1.0, getZ(), 30, 1.2, 0.6, 1.2, 0.0);
		level.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY(), getZ(), 20, 0.8, 0.6, 0.8, 0.1);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.8f, 0.5f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.4f, 0.7f);
		return n;
	}

	private static void ram(ServerPlayer owner, LivingEntity e, float damage, Vec3 dir, double force) {
		AbilityHelpers.hurtBurst(owner, e, damage);
		e.clearFire();
		if (!com.projecthero.mod.titanshifter.TitanCombat.isBoss(e) && e.getMaxHealth() <= 200.0f) {
			Vec3 flat = new Vec3(dir.x, 0, dir.z);
			flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
			e.setDeltaMovement(flat.x * 1.9 * force, 0.65 * force + 0.1, flat.z * 1.9 * force);
			e.hurtMarked = true;
			e.hasImpulse = true;
		}
	}

	/** The way it is flying (server), for the gametests. */
	public Vec3 velocity() {
		return vel;
	}
}
