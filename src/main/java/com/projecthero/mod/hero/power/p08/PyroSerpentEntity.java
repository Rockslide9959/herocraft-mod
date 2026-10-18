package com.projecthero.mod.hero.power.p08;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Pyrokinesis, hold G: the Flame Serpent. A serpent of living fire (drawn as a writhing chain of molten segments
 * with a horned, open-jawed head by {@code PyroSerpentRenderer}) that coils off the caster's arm for half a second, then
 * hunts {@link PyrokinesisHandlers#serpentQuarry what the caster aims at, or the nearest threat within 25 blocks}, strikes it once and bursts. Never
 * saved; it goes out with its caster (logout, death, another power) or after 6 s.
 *
 * <p>The server moves it every tick (synced each tick); the client keeps the trail of where its head has been
 * ({@link #trail()}) and the body is laid along it.
 */
public class PyroSerpentEntity extends Entity {
	public static final int COIL_TICKS = 10;
	public static final int LIFE_TICKS = 6 * 20;
	private static final double SPEED = 0.95;

	private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(PyroSerpentEntity.class, EntityDataSerializers.INT);

	private UUID ownerId;
	private float damage = PyrokinesisHandlers.SERPENT_DAMAGE;
	private Vec3 vel = Vec3.ZERO;
	private int age;
	/** Client: where the head has been, newest first. */
	private final Deque<Vec3> trail = new ArrayDeque<>();

	public PyroSerpentEntity(EntityType<? extends PyroSerpentEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	public static PyroSerpentEntity spawn(ServerLevel level, ServerPlayer owner, float damage) {
		PyroSerpentEntity e = new PyroSerpentEntity(PyroEntities.SERPENT, level);
		Vec3 h = AbilityHelpers.handPosition(owner);
		e.moveTo(h.x, h.y, h.z, owner.getYRot(), owner.getXRot());
		e.ownerId = owner.getUUID();
		e.damage = damage;
		e.vel = owner.getLookAngle().scale(0.3);
		level.addFreshEntity(e);
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(TARGET, -1);
	}

	/** The quarry's entity id, or -1. */
	public int targetId() {
		return entityData.get(TARGET);
	}

	public float damage() {
		return damage;
	}

	/** Client: the head's recent positions, newest first (world space). */
	public Deque<Vec3> trail() {
		return trail;
	}

	private ServerPlayer owner(ServerLevel sl) {
		return ownerId != null && sl.getPlayerByUUID(ownerId) instanceof ServerPlayer sp ? sp : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel sl)) {
			trail.addFirst(position());
			while (trail.size() > 48) {
				trail.removeLast();
			}
			return;
		}
		age++;
		ServerPlayer owner = owner(sl);
		Power active = owner == null ? null : ExperimentalPowers.getActive(owner);
		if (owner == null || !owner.isAlive() || active == null || !PyrokinesisHandlers.KEY.equals(active.key()) || age > LIFE_TICKS) {
			fizzle(sl);
			return;
		}
		Vec3 pos = position();
		Vec3 next;
		if (age <= COIL_TICKS) {
			// coils round the arm: a spiral round the aim, out from the hand
			Vec3 look = owner.getLookAngle();
			Vec3 right = look.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
			Vec3 up = right.cross(look).normalize();
			double f = age / (double) COIL_TICKS;
			double ang = age * 1.25;
			double r = 0.45 + 0.25 * f;
			Vec3 hand = AbilityHelpers.handPosition(owner);
			next = hand.add(look.scale(-0.3 + f * 1.6)).add(right.scale(Math.cos(ang) * r)).add(up.scale(Math.sin(ang) * r));
			vel = next.subtract(pos);
		} else {
			LivingEntity t = level().getEntity(targetId()) instanceof LivingEntity le && le.isAlive() ? le : null;
			if (t == null && (age == COIL_TICKS + 1 || age % 5 == 0)) {
				t = PyrokinesisHandlers.serpentQuarry(owner);
				entityData.set(TARGET, t == null ? -1 : t.getId());
			}
			if (age == COIL_TICKS + 1) {
				vel = owner.getLookAngle().scale(SPEED);
				sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2f, 0.6f);
			}
			if (t != null) {
				Vec3 aim = t.position().add(0, t.getBbHeight() * 0.55, 0);
				Vec3 want = aim.subtract(pos);
				double d = want.length();
				if (d < 0.9 + t.getBbWidth() * 0.5) {
					PyrokinesisHandlers.serpentStrike(owner, sl, t, damage);
					discard();
					return;
				}
				// steer: a limited turn toward the quarry, so it swings round in arcs
				Vec3 steer = want.scale(1.0 / d).scale(SPEED);
				vel = vel.add(steer.subtract(vel).scale(age < COIL_TICKS + 8 ? 0.18 : 0.32));
			}
			double len = vel.length();
			vel = len < 1.0e-4 ? owner.getLookAngle().scale(SPEED) : vel.scale(SPEED / len);
			// over walls rather than through them
			BlockPos ahead = BlockPos.containing(pos.add(vel));
			if (!sl.getBlockState(ahead).getCollisionShape(sl, ahead).isEmpty()) {
				vel = vel.add(0, 0.5, 0).normalize().scale(SPEED);
			}
			next = pos.add(vel);
		}
		setPos(next.x, next.y, next.z);
		if (vel.lengthSqr() > 1.0e-6) {
			setYRot((float) (Math.atan2(-vel.x, vel.z) * 180.0 / Math.PI));
			setXRot((float) (-Math.asin(Math.max(-1.0, Math.min(1.0, vel.normalize().y))) * 180.0 / Math.PI));
		}
		if (age % 2 == 0) {
			sl.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 2, 0.15, 0.15, 0.15, 0.01);
		}
		if (age % 12 == 0) {
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 0.8f, 0.6f);
		}
	}

	private void fizzle(ServerLevel sl) {
		sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 10, 0.3, 0.3, 0.3, 0.02);
		sl.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 16, 0.3, 0.3, 0.3, 0.05);
		sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.2f);
		discard();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 128.0 * 128.0;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
