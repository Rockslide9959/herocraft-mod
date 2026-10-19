package com.projecthero.mod.miraculous.renarouge.entity;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * v0.16.7: the big visual of one Mirage-wheel illusion -- a marker entity the client draws (all of it client-side, so a
 * 12-block fire field or a 7-block beast costs the server one tracked entity, no particle packets). Kinds:
 * {@link #MONSTER} the giant illusory fox-beast, {@link #ZONE} the burning, cracking ground, {@link #LURE} the heap of
 * treasure, {@link #PIT} the illusory floor over the Pitfall, {@link #ECLIPSE} the dome of night, {@link #MIRRORS} the
 * ring of mirrors. The server ({@code RenaMirage}) owns the effect and removes this when the illusion ends; it also goes
 * by itself a little after {@link #life} as a safety net. Never saved.
 */
public class MirageFxEntity extends Entity {
	public static final int MONSTER = 0;
	public static final int ZONE = 1;
	public static final int LURE = 2;
	public static final int PIT = 3;
	public static final int ECLIPSE = 4;
	public static final int MIRRORS = 5;

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(MirageFxEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(MirageFxEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(MirageFxEntity.class, EntityDataSerializers.INT);
	/** The Pitfall floor's look (the ground block it hides). */
	private static final EntityDataAccessor<Integer> BLOCK = SynchedEntityData.defineId(MirageFxEntity.class, EntityDataSerializers.INT);
	/** The Pitfall's width (blocks). */
	private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(MirageFxEntity.class, EntityDataSerializers.INT);
	/** Which way the Monster faces (degrees). */
	private static final EntityDataAccessor<Float> FACING = SynchedEntityData.defineId(MirageFxEntity.class, EntityDataSerializers.FLOAT);

	private @Nullable UUID owner;
	private int age;

	public MirageFxEntity(EntityType<? extends MirageFxEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, MONSTER);
		builder.define(RADIUS, 4f);
		builder.define(LIFE, 600);
		builder.define(BLOCK, Block.getId(Blocks.GRASS_BLOCK.defaultBlockState()));
		builder.define(SIZE, 4);
		builder.define(FACING, 0f);
	}

	public void setup(UUID ownerId, int kind, float radius, int lifeTicks) {
		owner = ownerId;
		entityData.set(KIND, kind);
		entityData.set(RADIUS, radius);
		entityData.set(LIFE, lifeTicks);
	}

	public void setPit(BlockState look, int size) {
		entityData.set(BLOCK, Block.getId(look));
		entityData.set(SIZE, size);
	}

	public void setFacing(float yaw) {
		entityData.set(FACING, yaw);
	}

	public int kind() {
		return entityData.get(KIND);
	}

	public float radius() {
		return entityData.get(RADIUS);
	}

	public int life() {
		return entityData.get(LIFE);
	}

	public BlockState look() {
		return Block.stateById(entityData.get(BLOCK));
	}

	public int size() {
		return entityData.get(SIZE);
	}

	public float facing() {
		return entityData.get(FACING);
	}

	public @Nullable UUID ownerId() {
		return owner;
	}

	public int age() {
		return age;
	}

	/** 0..1 in over the first 10 ticks, 1..0 over the last 10 (client-safe). */
	public float fade(float partial) {
		float a = tickCount + partial;
		return Mth.clamp(Math.min(a / 10f, (life() - a) / 10f), 0f, 1f);
	}

	@Override
	public void tick() {
		super.tick();
		age++;
		if (level().isClientSide) {
			ambient();
			return;
		}
		if (age > life() + 40) {
			discard();
		}
	}

	/** Client only: a few cheap local particles (never sent over the network). */
	private void ambient() {
		float r = radius();
		switch (kind()) {
			case ZONE -> {
				for (int i = 0; i < 3; i++) {
					double a = random.nextDouble() * Math.PI * 2;
					double d = Math.sqrt(random.nextDouble()) * r;
					double x = getX() + Math.cos(a) * d;
					double z = getZ() + Math.sin(a) * d;
					level().addParticle(i == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, x, getY() + 0.1 + random.nextDouble() * 0.6, z, 0,
							0.03 + random.nextDouble() * 0.05, 0);
				}
				if (random.nextInt(6) == 0) {
					double a = random.nextDouble() * Math.PI * 2;
					double d = random.nextDouble() * r;
					level().addParticle(ParticleTypes.LAVA, getX() + Math.cos(a) * d, getY() + 0.2, getZ() + Math.sin(a) * d, 0, 0, 0);
				}
			}
			case LURE -> {
				if (tickCount % 2 == 0) {
					level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 2.5, getY() + 0.3 + random.nextDouble() * 1.8,
							getZ() + (random.nextDouble() - 0.5) * 2.5, 0, 0.02, 0);
				}
			}
			case ECLIPSE -> {
				if (tickCount % 3 == 0) {
					double a = random.nextDouble() * Math.PI * 2;
					double d = random.nextDouble() * r;
					level().addParticle(ParticleTypes.SQUID_INK, getX() + Math.cos(a) * d, getY() + random.nextDouble() * 3, getZ() + Math.sin(a) * d, 0,
							0.01, 0);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 160 * 160;
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		float r = Math.max(radius(), 4f);
		return new AABB(getX() - r, getY() - r, getZ() - r, getX() + r, getY() + Math.max(r, 10f), getZ() + r);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
