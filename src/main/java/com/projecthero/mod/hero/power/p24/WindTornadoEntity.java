package com.projecthero.mod.hero.power.p24;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Wind Manipulation's H: a rideable tornado. Spawned under the caster, who rides on top of it; while ridden it drifts
 * the way the rider looks, hugging the terrain (and water). It drags every enemy within 8 blocks into a spiral, lifts
 * and grinds whatever reaches the funnel, and blows itself out after {@link #MAX_LIFE} ticks (or when the rider presses
 * H again). Never saved, never collides, cannot be hurt.
 */
public class WindTornadoEntity extends Entity {
	public static final int MAX_LIFE = 240;
	public static final double PULL_RADIUS = 8.0;
	private static final double SPEED = 0.42;
	private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(WindTornadoEntity.class,
			EntityDataSerializers.INT);

	private UUID owner;
	private Vec3 drift = Vec3.ZERO;

	public WindTornadoEntity(EntityType<? extends WindTornadoEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public WindTornadoEntity(Level level, ServerPlayer owner) {
		this(WindEntities.TORNADO, level);
		this.owner = owner.getUUID();
		this.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(LIFE, MAX_LIFE);
	}

	/** Ticks left before it blows itself out (synced, so the renderer can fade it). */
	public int life() {
		return entityData.get(LIFE);
	}

	public UUID owner() {
		return owner;
	}

	/** Ends the tornado now (H pressed again, the rider left the world ...). */
	public void dissipate() {
		if (level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 2, getZ(), 40, 1.2, 2.0, 1.2, 0.08);
			sl.playSound(null, blockPosition(), SoundEvents.BREEZE_DEATH, SoundSource.PLAYERS, 0.8f, 0.7f);
		}
		ejectPassengers();
		discard();
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		int life = life() - 1;
		entityData.set(LIFE, life);
		ServerPlayer ownerPlayer = owner == null ? null : (ServerPlayer) level.getPlayerByUUID(owner);
		if (life <= 0 || ownerPlayer == null) {
			dissipate();
			return;
		}

		// steering: follow the rider's look (horizontally), otherwise slow to a stop
		Entity rider = getFirstPassenger();
		Vec3 desired = Vec3.ZERO;
		if (rider instanceof ServerPlayer rp) {
			Vec3 look = rp.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			if (flat.lengthSqr() > 1.0e-4) {
				desired = flat.normalize().scale(SPEED);
			}
			rp.resetFallDistance();
		}
		drift = drift.scale(0.8).add(desired.scale(0.2));
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(getX() + drift.x),
				(int) Math.floor(getZ() + drift.z));
		double dy = Math.max(-0.4, Math.min(0.5, surface - getY()));
		Vec3 move = new Vec3(drift.x, dy, drift.z);
		setDeltaMovement(move);
		move(MoverType.SELF, move);

		// the pull: spiral inward, lift near the core, grind inside it
		Vec3 axis = position();
		for (LivingEntity e : AbilityHelpers.hostilesAround(ownerPlayer, axis.add(0, 2.5, 0), PULL_RADIUS)) { // v0.14.20: roaming field, rule 2
			if (e == rider || e == ownerPlayer || e.isPassengerOfSameVehicle(this)) {
				continue;
			}
			Vec3 rel = new Vec3(axis.x - e.getX(), 0, axis.z - e.getZ());
			double d = Math.max(0.3, rel.length());
			Vec3 in = rel.normalize();
			Vec3 spin = new Vec3(-in.z, 0, in.x);
			double lift = d < 3.5 ? 0.22 : 0.04;
			Vec3 v = in.scale(Math.min(0.35, 0.12 + 0.6 / d)).add(spin.scale(0.28)).add(0, lift, 0);
			Vec3 cur = e.getDeltaMovement();
			e.setDeltaMovement(new Vec3(cur.x * 0.6 + v.x, Math.min(0.55, cur.y * 0.6 + v.y), cur.z * 0.6 + v.z));
			e.hurtMarked = true;
			e.resetFallDistance();
			if (d < 3.2 && tickCount % 20 == 0) {
				AbilityHelpers.hurt(ownerPlayer, e, WindHandlers.TORNADO_DPS);
			}
		}

		// visuals everyone sees on top of the mesh: a spiral of cloud and dust
		if (tickCount % 2 == 0) {
			for (int i = 0; i < 6; i++) {
				double h = (i + (tickCount % 8) / 8.0) * 0.9;
				double a = tickCount * 0.45 + i * 1.3;
				double r = 0.5 + h * 0.4;
				level.sendParticles(ParticleTypes.CLOUD, getX() + Math.cos(a) * r, getY() + h, getZ() + Math.sin(a) * r,
						0, -Math.sin(a), 0.15, Math.cos(a), 0.12);
			}
		}
		if (tickCount % 30 == 0) {
			level.playSound(null, blockPosition(), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1.0f, 0.6f);
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (passenger instanceof ServerPlayer sp) {
			WindHandlers.protectFall(sp, 100);
		}
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
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("owner")) {
			owner = tag.getUUID("owner");
		}
		entityData.set(LIFE, tag.getInt("life"));
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (owner != null) {
			tag.putUUID("owner", owner);
		}
		tag.putInt("life", life());
	}
}
