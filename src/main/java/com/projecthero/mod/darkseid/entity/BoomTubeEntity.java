package com.projecthero.mod.darkseid.entity;

import java.util.UUID;

import com.projecthero.mod.darkseid.DarkseidSounds;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Boom Tube: Apokolips' crackling white-blue doorway. Purely a visual -- it has no collision, is never saved and
 * removes itself after {@link #life} ticks -- so the raid can open as many as it needs without leaving anything
 * behind. What comes <em>through</em> it (Parademons, Darkseid himself) is spawned by the raid manager at its
 * position; the tube is only the show. The renderer draws it as a camera-facing disc sized by
 * {@link #openFraction}.
 */
public class BoomTubeEntity extends Entity {
	public enum Kind {
		/** Darkseid's arrival: huge and slow. */
		ENTRANCE,
		/** Melee Parademons. */
		MELEE,
		/** Ranged Parademons (opens in the air). */
		RANGED,
		/** Elite / Brute Parademons (tinged red). */
		ELITE,
		/** A Mother Box overload. */
		OVERLOAD,
		/** Darkseid leaving at his death. */
		EXIT,
		/** The quick blink of an Omega Teleport. */
		FLASH
	}

	private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(BoomTubeEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(BoomTubeEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(BoomTubeEntity.class, EntityDataSerializers.INT);

	private UUID raidId;

	public BoomTubeEntity(EntityType<? extends BoomTubeEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Open a tube at {@code center} (its middle) for {@code life} ticks. */
	public static BoomTubeEntity open(ServerLevel level, Vec3 center, Kind kind, float size, int life, UUID raidId) {
		BoomTubeEntity tube = new BoomTubeEntity(DarkseidEntityTypes.BOOM_TUBE, level);
		tube.setPos(center.x, center.y - 0.25, center.z);
		tube.entityData.set(DATA_KIND, (byte) kind.ordinal());
		tube.entityData.set(DATA_SIZE, size);
		tube.entityData.set(DATA_LIFE, life);
		tube.raidId = raidId;
		level.addFreshEntity(tube);
		if (kind != Kind.FLASH) {
			level.playSound(null, center.x, center.y, center.z, DarkseidSounds.BOOM_TUBE, SoundSource.HOSTILE,
					kind == Kind.ENTRANCE || kind == Kind.EXIT ? 5.0f : 2.5f, kind == Kind.ENTRANCE ? 0.6f : 1.0f);
			level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
		}
		return tube;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_KIND, (byte) Kind.MELEE.ordinal());
		builder.define(DATA_SIZE, 2.5f);
		builder.define(DATA_LIFE, 60);
	}

	public Kind kind() {
		int k = entityData.get(DATA_KIND);
		return k >= 0 && k < Kind.values().length ? Kind.values()[k] : Kind.MELEE;
	}

	public float size() {
		return entityData.get(DATA_SIZE);
	}

	public int life() {
		return entityData.get(DATA_LIFE);
	}

	public UUID raidId() {
		return raidId;
	}

	/** 0 -> 1 over the first quarter-second, held, then back to 0 over the last quarter-second. */
	public float openFraction(float partialTick) {
		float age = tickCount + partialTick;
		int life = life();
		float ramp = Math.min(6.0f, life / 3.0f);
		if (age < ramp) {
			return Math.max(0.0f, age / ramp);
		}
		if (age > life - ramp) {
			return Math.max(0.0f, (life - age) / ramp);
		}
		return 1.0f;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (tickCount >= life()) {
			discard();
			return;
		}
		float r = size() * 0.5f * openFraction(0.0f);
		if (r > 0.2f && tickCount % 3 == 0 && kind() != Kind.FLASH) {
			server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.25, getZ(), 3, r * 0.6, r * 0.6, r * 0.6, 0.02);
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.25, getZ(), 2, r * 0.7, r * 0.7, r * 0.7, 0.1);
		}
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
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 160.0 * 160.0;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
