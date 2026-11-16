package com.projecthero.mod.powerarmour.entity;

import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;

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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Power Armour: the Sentry Turret a suit drops (C with the turret on the back). It stands for its lifetime, picks the
 * nearest threat to its owner within 16 blocks that it can see, and shoots it every 8 ticks. Never hurt, never pushed; one per
 * owner (a new one replaces the old); gone when the owner is.
 */
public class SentryTurretEntity extends Entity {
	private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(SentryTurretEntity.class, EntityDataSerializers.INT);
	private static final double RANGE = 16.0;

	private UUID owner;
	private float damage = 3f;
	private int life = 600;

	public SentryTurretEntity(EntityType<? extends SentryTurretEntity> type, Level level) {
		super(type, level);
	}

	public void setup(ServerPlayer owner, float damage, int life) {
		this.owner = owner.getUUID();
		this.damage = damage;
		this.life = life;
	}

	public UUID owner() {
		return owner;
	}

	/** The entity it aims at (-1 none), synced for the head. */
	public int target() {
		return entityData.get(DATA_TARGET);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_TARGET, -1);
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 v = getDeltaMovement();
		setDeltaMovement(0, onGround() ? 0 : Math.max(-1.0, v.y - 0.08), 0);
		move(MoverType.SELF, getDeltaMovement());
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer p = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
		if (p == null || p.level() != level || --life <= 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 10, 0.2, 0.3, 0.2, 0.02);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.7f, 0.6f);
			discard();
			return;
		}
		Vec3 eye = position().add(0, 0.85, 0);
		if (tickCount % 10 == 0) {
			LivingEntity best = null;
			double bestD = Double.MAX_VALUE;
			for (LivingEntity e : AbilityHelpers.hostilesAround(p, eye, RANGE)) {
				Vec3 at = e.position().add(0, e.getBbHeight() * 0.6, 0);
				if (level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) {
					continue;
				}
				double d = e.distanceToSqr(this);
				if (d < bestD) {
					bestD = d;
					best = e;
				}
			}
			entityData.set(DATA_TARGET, best == null ? -1 : best.getId());
		}
		if (tickCount % 8 == 0 && target() >= 0 && level.getEntity(target()) instanceof LivingEntity t && t.isAlive()) {
			Vec3 at = t.position().add(0, t.getBbHeight() * 0.6, 0);
			AbilityHelpers.hurtBurst(p, t, damage);
			AbilityHelpers.line(level, eye, at, ParticleTypes.SMALL_FLAME, 1.5);
			level.playSound(null, getX(), getY() + 0.8, getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.4f, 1.8f);
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("Owner")) {
			owner = tag.getUUID("Owner");
		}
		damage = tag.getFloat("Damage");
		life = tag.getInt("Life");
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (owner != null) {
			tag.putUUID("Owner", owner);
		}
		tag.putFloat("Damage", damage);
		tag.putInt("Life", life);
	}

	/** The way the head faces (yaw, degrees) toward {@code target}, client side. */
	public static float yawTo(Entity self, Entity target) {
		Vec3 d = target.position().subtract(self.position());
		return (float) (Math.atan2(d.z, d.x) * 180.0 / Math.PI) - 90f;
	}
}
